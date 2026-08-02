#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from typing import Any

BASE_URL = os.environ.get("WUST_DORMITORY_BASE_URL", "http://127.0.0.1:8080").rstrip("/")


def request(
    method: str,
    path: str,
    *,
    token: str | None = None,
    body: Any | None = None,
    expected_status: int = 200,
) -> dict[str, Any]:
    headers = {
        "Accept": "application/json",
        "X-Request-Id": "student-experience-smoke",
    }
    payload = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(BASE_URL + path, data=payload, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            status = response.status
            raw = response.read()
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read()
    if status != expected_status:
        raise AssertionError(
            f"{method} {path}: expected {expected_status}, got {status}: "
            f"{raw.decode('utf-8', errors='replace')}"
        )
    result = json.loads(raw.decode("utf-8"))
    if expected_status < 400:
        assert result.get("success") is True, result
    return result


def data(response: dict[str, Any]) -> Any:
    return response["data"]


def main() -> int:
    admin = data(request(
        "POST",
        "/api/v1/auth/login",
        body={"username": "admin", "password": "Dormitory@2026"},
    ))
    admin_token = admin["accessToken"]

    current_setting = data(request(
        "GET",
        "/api/v1/admin/settings/student-welcome",
        token=admin_token,
    ))
    configured_message = "欢迎新同学！请先完成个人偏好，再选择适合自己的宿舍和床位。"
    updated_setting = data(request(
        "PUT",
        "/api/v1/admin/settings/student-welcome",
        token=admin_token,
        body={
            "message": configured_message,
            "expectedVersion": int(current_setting["version"]),
        },
    ))
    assert updated_setting["message"] == configured_message, updated_setting
    assert int(updated_setting["version"]) == int(current_setting["version"]) + 1

    stale_update = request(
        "PUT",
        "/api/v1/admin/settings/student-welcome",
        token=admin_token,
        body={
            "message": "此请求应因版本过期而失败",
            "expectedVersion": int(current_setting["version"]),
        },
        expected_status=409,
    )
    assert stale_update["error"]["code"] == "SYSTEM_SETTING_VERSION_CONFLICT", stale_update

    request(
        "POST",
        "/api/v1/auth/activate",
        body={
            "studentNumber": "202600000002",
            "studentName": "测试男生002",
            "password": "StudentWelcome2026",
        },
    )
    first_login = data(request(
        "POST",
        "/api/v1/auth/login",
        body={
            "username": "202600000002",
            "password": "StudentWelcome2026",
        },
    ))
    student_token = first_login["accessToken"]
    welcome = first_login["user"]["welcome"]
    assert welcome["required"] is True, welcome
    assert welcome["title"] == "新同学，欢迎你", welcome
    assert welcome["message"] == configured_message, welcome

    request(
        "POST",
        "/api/v1/auth/welcome/acknowledge",
        token=student_token,
    )
    request(
        "POST",
        "/api/v1/auth/welcome/acknowledge",
        token=student_token,
    )
    current_user = data(request("GET", "/api/v1/auth/me", token=student_token))
    assert current_user["welcome"]["required"] is False, current_user

    second_login = data(request(
        "POST",
        "/api/v1/auth/login",
        body={
            "username": "202600000002",
            "password": "StudentWelcome2026",
        },
    ))
    assert second_login["user"]["welcome"]["required"] is False, second_login

    print("Student experience HTTP smoke flow passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
