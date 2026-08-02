#!/usr/bin/env python3
"""Runtime smoke test for the single-client platform subscription and entitlement flow."""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request

BASE_URL = os.environ.get("BASE_URL", "http://127.0.0.1:8080")
USERNAME = os.environ.get("PLATFORM_ADMIN_USERNAME", "system_admin")
PASSWORD = os.environ.get("PLATFORM_ADMIN_PASSWORD", "Dormitory@2026")
NEW_PASSWORD = os.environ.get("PLATFORM_ADMIN_NEW_PASSWORD", "Dormitory@Test2026!")


def call(method: str, path: str, body=None, token: str | None = None, expected=(200,)):
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = response.read().decode("utf-8")
            assert response.status in expected, (path, response.status, payload)
            return json.loads(payload) if payload else None
    except urllib.error.HTTPError as error:
        payload = error.read().decode("utf-8")
        if error.code not in expected:
            raise AssertionError((path, error.code, payload)) from error
        return json.loads(payload) if payload else None


def main() -> None:
    login = call("POST", "/api/v1/platform/login", {"username": USERNAME, "password": PASSWORD})
    token = login["accessToken"]
    user = login["user"]
    assert user["userType"] == "SYSTEM_ADMIN"

    business_rejection = call("GET", "/api/v1/admin/batches", token=token, expected=(403,))
    assert business_rejection is not None

    if user.get("passwordChangeRequired"):
        call("POST", "/api/v1/platform/password", {
            "currentPassword": PASSWORD,
            "newPassword": NEW_PASSWORD,
        }, token=token)
        login = call("POST", "/api/v1/platform/login", {"username": USERNAME, "password": NEW_PASSWORD})
        token = login["accessToken"]
        assert login["user"]["passwordChangeRequired"] is False

    subscription = call("GET", "/api/v1/platform/subscription", token=token)
    assert subscription["subscriptionType"] == "LONG_TERM"
    assert subscription.get("endAt") is None

    plans = call("GET", "/api/v1/platform/plans", token=token)
    assert plans
    features = call("GET", "/api/v1/platform/features", token=token)
    codes = {item["feature_code"] if "feature_code" in item else item.get("featureCode") for item in features}
    assert "P1_DORMITORY_BASIC" in codes
    assert "P2_BATCH_COPY" in codes
    assert "P3_ROOM_CHANGE_REQUEST" in codes

    quotas = call("GET", "/api/v1/platform/quotas", token=token)
    assert "effective" in quotas and "usage" in quotas
    audit = call("GET", "/api/v1/platform/audit?limit=20", token=token)
    assert audit

    print("platform subscription entitlement smoke: PASS")
    if user.get("passwordChangeRequired"):
        print("注意：测试已把系统管理员密码修改为 PLATFORM_ADMIN_NEW_PASSWORD 指定值。")


if __name__ == "__main__":
    main()
