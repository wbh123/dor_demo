#!/usr/bin/env python3
from __future__ import annotations

import ipaddress
import socket
from dataclasses import dataclass
from typing import BinaryIO


class RedisCommandError(RuntimeError):
    """Redis 返回错误响应。"""


@dataclass(frozen=True)
class RedisClearResult:
    database: int
    before_keys: int
    after_keys: int


def validate_redis_database(database: int) -> int:
    if database < 0:
        raise ValueError("Redis database 编号不能小于0")
    return database


def validate_redis_target(host: str, allow_remote: bool) -> None:
    if allow_remote:
        return
    normalized = host.strip().lower().strip("[]")
    if normalized == "localhost":
        return
    try:
        address = ipaddress.ip_address(normalized)
    except ValueError as exception:
        raise ValueError(
            f"拒绝清理非本机 Redis：{host}。确认是开发/测试实例后显式允许远程目标"
        ) from exception
    if not address.is_loopback:
        raise ValueError(
            f"拒绝清理非本机 Redis：{host}。确认是开发/测试实例后显式允许远程目标"
        )


def should_clear_redis(*, check: bool, skip_redis: bool) -> bool:
    return not check and not skip_redis


def encode_redis_command(*parts: object) -> bytes:
    encoded = [str(part).encode("utf-8") for part in parts]
    chunks = [f"*{len(encoded)}\r\n".encode("ascii")]
    for value in encoded:
        chunks.append(f"${len(value)}\r\n".encode("ascii"))
        chunks.append(value)
        chunks.append(b"\r\n")
    return b"".join(chunks)


def read_redis_response(reader: BinaryIO):
    prefix = reader.read(1)
    if not prefix:
        raise RedisCommandError("Redis 连接已关闭")
    line = reader.readline()
    if not line.endswith(b"\r\n"):
        raise RedisCommandError("Redis 返回了不完整响应")
    payload = line[:-2]
    if prefix == b"+":
        return payload.decode("utf-8")
    if prefix == b"-":
        raise RedisCommandError(payload.decode("utf-8", errors="replace"))
    if prefix == b":":
        return int(payload)
    if prefix == b"$":
        length = int(payload)
        if length == -1:
            return None
        value = reader.read(length)
        if reader.read(2) != b"\r\n":
            raise RedisCommandError("Redis bulk string 响应不完整")
        return value.decode("utf-8")
    if prefix == b"*":
        count = int(payload)
        if count == -1:
            return None
        return [read_redis_response(reader) for _ in range(count)]
    raise RedisCommandError(f"未知 Redis 响应类型：{prefix!r}")


def clear_redis_database(
    *,
    host: str,
    port: int,
    password: str,
    database: int,
    timeout_seconds: float,
    username: str = "",
) -> RedisClearResult:
    database = validate_redis_database(database)
    if port <= 0 or port > 65535:
        raise ValueError("Redis端口必须在1到65535之间")
    if timeout_seconds <= 0:
        raise ValueError("Redis连接超时必须大于0")

    with socket.create_connection((host, port), timeout=timeout_seconds) as connection:
        connection.settimeout(timeout_seconds)
        reader = connection.makefile("rb")

        def command(*parts: object):
            connection.sendall(encode_redis_command(*parts))
            return read_redis_response(reader)

        if password:
            if username:
                command("AUTH", username, password)
            else:
                command("AUTH", password)
        command("SELECT", database)
        before = command("DBSIZE")
        if not isinstance(before, int):
            raise RedisCommandError("DBSIZE未返回整数")
        command("FLUSHDB")
        after = command("DBSIZE")
        if not isinstance(after, int):
            raise RedisCommandError("DBSIZE未返回整数")
        if after != 0:
            raise RedisCommandError(f"FLUSHDB执行后仍有{after}个键")
        return RedisClearResult(database=database, before_keys=before, after_keys=after)
