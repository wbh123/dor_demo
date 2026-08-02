#!/usr/bin/env python3
"""Reset the unique SYSTEM_ADMIN password without printing or storing plaintext."""

from __future__ import annotations

import argparse
import getpass
import json
import os
import re
import sys
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="重置唯一系统管理员密码")
    parser.add_argument("--password", help="新密码；省略时使用隐藏输入")
    return parser.parse_args()


def validate_password(password: str) -> None:
    checks = (
        len(password) >= 12,
        re.search(r"[A-Z]", password),
        re.search(r"[a-z]", password),
        re.search(r"\d", password),
        re.search(r"[^A-Za-z0-9]", password),
    )
    if not all(checks):
        raise ValueError("密码至少12位，并包含大小写字母、数字和特殊字符")


def database_config() -> dict[str, Any]:
    return {
        "host": os.environ.get("MYSQL_HOST", "127.0.0.1"),
        "port": int(os.environ.get("MYSQL_PORT", "3306")),
        "user": os.environ.get("MYSQL_USER", "dormitory"),
        "password": os.environ.get("MYSQL_PASSWORD", ""),
        "database": os.environ.get("MYSQL_DATABASE", "wust_dormitory"),
        "charset": "utf8mb4",
        "autocommit": False,
    }


def revoke_cached_tokens(system_admin_id: int) -> int:
    try:
        import redis
    except ImportError:
        print("警告：未安装redis模块，数据库密码已重置后需重启服务以清理旧令牌。", file=sys.stderr)
        return 0
    client = redis.Redis(
        host=os.environ.get("REDIS_HOST", "127.0.0.1"),
        port=int(os.environ.get("REDIS_PORT", "6379")),
        password=os.environ.get("REDIS_PASSWORD") or None,
        db=int(os.environ.get("REDIS_DATABASE", "0")),
        decode_responses=True,
    )
    removed = 0
    prefix = os.environ.get("AUTH_TOKEN_PREFIX", "auth:token:")
    for key in client.scan_iter(match=f"{prefix}*"):
        raw = client.get(key)
        if not raw:
            continue
        try:
            value = json.loads(raw)
        except json.JSONDecodeError:
            continue
        user_id = value.get("userId") if isinstance(value, dict) else None
        if user_id is not None and int(user_id) == system_admin_id:
            removed += int(client.delete(key))
    return removed


def main() -> int:
    args = parse_args()
    password = args.password or getpass.getpass("请输入新的系统管理员密码：")
    confirm = password if args.password else getpass.getpass("请再次输入新密码：")
    if password != confirm:
        print("两次输入的密码不一致。", file=sys.stderr)
        return 2
    try:
        validate_password(password)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    try:
        import bcrypt
        import pymysql
    except ImportError:
        print("缺少依赖，请安装：python -m pip install bcrypt pymysql redis", file=sys.stderr)
        return 3

    password_hash = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=10)).decode("ascii")
    connection = pymysql.connect(**database_config())
    system_admin_id: int | None = None
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT id FROM app_user WHERE user_type='SYSTEM_ADMIN' FOR UPDATE")
            rows = cursor.fetchall()
            if len(rows) != 1:
                raise RuntimeError(f"期望唯一SYSTEM_ADMIN，实际数量为{len(rows)}")
            system_admin_id = int(rows[0][0])
            cursor.execute(
                """
                UPDATE app_user
                SET password_hash=%s, password_change_required=1,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=%s AND user_type='SYSTEM_ADMIN'
                """,
                (password_hash, system_admin_id),
            )
            cursor.execute(
                """
                INSERT INTO platform_audit_log
                (operation_type, operator_user_id, target_type, target_id,
                 change_reason, after_json, success)
                VALUES ('SYSTEM_ADMIN_PASSWORD_RESET', %s, 'APP_USER', %s,
                        '本地运维脚本重置系统管理员密码',
                        JSON_OBJECT('passwordChangeRequired', TRUE), 1)
                """,
                (system_admin_id, str(system_admin_id)),
            )
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()

    removed = revoke_cached_tokens(system_admin_id)
    print(f"系统管理员密码已重置；已清理{removed}个缓存令牌。下次登录必须修改密码。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
