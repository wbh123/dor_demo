#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
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
    headers = {"Accept": "application/json", "X-Request-Id": "admin-allocation-reset-smoke"}
    payload = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(BASE_URL + path, data=payload, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
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
    token = admin["accessToken"]

    batches = data(request("GET", "/api/v1/admin/batches", token=token))
    batch = next(item for item in batches if int(item["id"]) == 1)
    status = str(batch["batch_status"])
    if status == "PAUSED":
        request("POST", "/api/v1/admin/batches/1/status/OPEN", token=token)
        status = "OPEN"
    if status == "OPEN":
        request("POST", "/api/v1/admin/batches/1/status/CLOSED", token=token)
        status = "CLOSED"
    assert status in {"CLOSED", "ALLOCATING"}, status

    preview = data(request(
        "GET",
        "/api/v1/admin/batches/1/allocation/preview?randomSeed=20260802",
        token=token,
    ))
    summary = preview["summary"]
    assert summary["allStudentsIncluded"] is True, summary
    assert int(summary["studentCount"]) == 20, summary
    assert int(summary["unassignedCount"]) == 0, preview.get("unassigned")
    assert all("studentName" in item for item in preview["assignments"]), preview["assignments"][:3]

    committed = data(request(
        "POST",
        "/api/v1/admin/batches/1/allocation/commit",
        token=token,
        body={
            "randomSeed": 20260802,
            "idempotencyKey": "admin-allocation-reset-smoke-v1",
        },
    ))
    assert int(committed["summary"]["unassignedCount"]) == 0, committed
    assert committed["unassigned"] == [], committed

    query = urllib.parse.quote("202600000001")
    student_page = data(request(
        "GET",
        f"/api/v1/admin/students?keyword={query}&page=1&size=20",
        token=token,
    ))
    student = student_page["items"][0]
    student_id = int(student["id"])
    student_number = str(student["student_number"])

    password_reset = data(request(
        "POST",
        f"/api/v1/admin/students/{student_id}/reset-password",
        token=token,
        body={"reason": "自动化验收学生密码重置"},
    ))
    assert password_reset["accountStatus"] == "PENDING", password_reset

    state_reset = data(request(
        "POST",
        f"/api/v1/admin/students/{student_id}/reset-state",
        token=token,
        body={
            "confirmStudentNumber": student_number,
            "reason": "自动化验收学生完整状态重置",
        },
    ))
    assert state_reset["accountStatus"] == "PENDING", state_reset
    assert int(state_reset["deleted"]["assignments"]) == 1, state_reset
    assert int(state_reset["deleted"]["batchEligibilities"]) == 1, state_reset

    assignments = data(request(
        "GET",
        "/api/v1/admin/batches/1/assignments",
        token=token,
    ))
    assert all(int(item["student_id"]) != student_id for item in assignments), assignments

    print("Admin all-student allocation and student reset HTTP smoke flow passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
