#!/usr/bin/env python3
from __future__ import annotations

import socketserver
import threading
import unittest

from redis_database_reset import (
    clear_redis_database,
    should_clear_redis,
    validate_redis_target,
)


class _RedisHandler(socketserver.StreamRequestHandler):
    commands: list[list[str]] = []

    def handle(self) -> None:
        while True:
            first = self.rfile.readline()
            if not first:
                return
            if not first.startswith(b"*"):
                return
            count = int(first[1:-2])
            parts: list[str] = []
            for _ in range(count):
                length_line = self.rfile.readline()
                length = int(length_line[1:-2])
                value = self.rfile.read(length)
                self.rfile.read(2)
                parts.append(value.decode("utf-8"))
            type(self).commands.append(parts)
            command = parts[0].upper()
            if command == "DBSIZE":
                value = 7 if len(type(self).commands) < 5 else 0
                self.wfile.write(f":{value}\r\n".encode("ascii"))
            else:
                self.wfile.write(b"+OK\r\n")


class RedisDatabaseResetTest(unittest.TestCase):
    def test_remote_redis_requires_explicit_permission(self) -> None:
        with self.assertRaises(ValueError):
            validate_redis_target("redis.example.internal", False)

    def test_loopback_redis_is_allowed_by_default(self) -> None:
        for host in ("localhost", "127.0.0.1", "::1"):
            with self.subTest(host=host):
                validate_redis_target(host, False)

    def test_clear_redis_database_authenticates_selects_and_flushes_only_selected_database(self) -> None:
        _RedisHandler.commands = []
        server = socketserver.TCPServer(("127.0.0.1", 0), _RedisHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            result = clear_redis_database(
                host="127.0.0.1",
                port=server.server_address[1],
                password="secret",
                database=3,
                timeout_seconds=2.0,
            )
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

        self.assertEqual(7, result.before_keys)
        self.assertEqual(0, result.after_keys)
        self.assertEqual(
            [
                ["AUTH", "secret"],
                ["SELECT", "3"],
                ["DBSIZE"],
                ["FLUSHDB"],
                ["DBSIZE"],
            ],
            _RedisHandler.commands,
        )

    def test_check_or_skip_mode_never_clears_redis(self) -> None:
        self.assertFalse(should_clear_redis(check=True, skip_redis=False))
        self.assertFalse(should_clear_redis(check=False, skip_redis=True))
        self.assertTrue(should_clear_redis(check=False, skip_redis=False))


if __name__ == "__main__":
    unittest.main(verbosity=2)
