#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
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
) -> Any:
    headers = {
        "Accept": "application/json",
        "X-Request-Id": f"phase1-smoke-{path.replace('/', '-')[:40]}",
    }
    payload = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(
        BASE_URL + path,
        data=payload,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            status = response.status
            content_type = response.headers.get("Content-Type", "")
            raw = response.read()
    except urllib.error.HTTPError as error:
        status = error.code
        content_type = error.headers.get("Content-Type", "")
        raw = error.read()

    if status != expected_status:
        raise AssertionError(
            f"{method} {path}: expected {expected_status}, got {status}: "
            f"{raw.decode('utf-8', errors='replace')}"
        )
    if not raw:
        return None
    if "json" not in content_type:
        return raw
    result = json.loads(raw.decode("utf-8"))
    if path.startswith("/api/") and isinstance(result, dict) and expected_status < 400:
        assert result.get("success") is True, result
        assert result.get("requestId"), result
    return result


def response_data(response: dict[str, Any]) -> Any:
    assert "data" in response, response
    return response["data"]


def main() -> int:
    health = request("GET", "/actuator/health")
    assert health["status"] == "UP", health

    admin_login = request(
        "POST",
        "/api/v1/auth/login",
        body={"username": "admin", "password": "Dormitory@2026"},
    )
    admin_token = response_data(admin_login)["accessToken"]
    assert admin_token

    dashboard = response_data(request("GET", "/api/v1/admin/dashboard", token=admin_token))
    assert int(dashboard["studentCount"]) == 520, dashboard
    assert int(dashboard["roomCount"]) == 144, dashboard
    assert int(dashboard["bedCount"]) == 640, dashboard

    majors = response_data(request("GET", "/api/v1/admin/majors", token=admin_token))
    assert len(majors) == 5, majors

    students = response_data(
        request(
            "GET",
            "/api/v1/admin/students?keyword=202600000001&page=1&size=20",
            token=admin_token,
        )
    )
    assert students["total"] == 1, students
    assert students["items"][0]["student_number"] == "202600000001", students

    buildings = response_data(request("GET", "/api/v1/admin/buildings", token=admin_token))
    assert len(buildings) == 8, buildings
    rooms = response_data(request("GET", "/api/v1/admin/rooms?gender=M", token=admin_token))
    assert len(rooms) == 64, len(rooms)

    request("POST", "/api/v1/admin/batches/1/prepare", token=admin_token)
    request("POST", "/api/v1/admin/batches/1/status/PUBLISHED", token=admin_token)
    request("POST", "/api/v1/admin/batches/1/status/OPEN", token=admin_token)

    request(
        "POST",
        "/api/v1/auth/activate",
        body={
            "studentNumber": "202600000001",
            "studentName": "测试男生001",
            "password": "StudentPassword2026",
        },
    )
    student_login = request(
        "POST",
        "/api/v1/auth/login",
        body={
            "username": "202600000001",
            "password": "StudentPassword2026",
        },
    )
    student_token = response_data(student_login)["accessToken"]

    profile = response_data(request("GET", "/api/v1/student/profile", token=student_token))
    assert profile["student_number"] == "202600000001", profile
    assert profile["major_code"] == "M001", profile

    questionnaire = response_data(
        request("GET", "/api/v1/student/batches/1/questionnaire", token=student_token)
    )
    assert len(questionnaire["questions"]) == 14, questionnaire
    request(
        "POST",
        "/api/v1/student/batches/1/questionnaire",
        token=student_token,
        body={
            "SLEEP_TIME": "23:00",
            "WAKE_TIME": "07:00",
            "NAP_HABIT": 1,
            "SLEEP_SENSITIVITY": 3,
            "NOISE_TOLERANCE": 3,
            "CLEANING_FREQUENCY": 4,
            "TIDINESS_REQUIREMENT": 4,
            "AC_TEMPERATURE": 25,
            "VENTILATION": 3,
            "STUDY_FREQUENCY": 3,
            "GAMING_VOICE": 2,
            "SOCIAL_ACTIVITY": 3,
            "SMOKING_ACCEPTANCE": False,
            "BED_PREFERENCE": "LOFT_BED_DESK",
        },
    )

    candidate_rooms = response_data(
        request("GET", "/api/v1/student/batches/1/rooms", token=student_token)
    )
    assert candidate_rooms, candidate_rooms
    room_id = int(candidate_rooms[0]["id"])
    room_snapshot = response_data(
        request(
            "GET",
            f"/api/v1/student/batches/1/rooms/{room_id}",
            token=student_token,
        )
    )
    available_beds = [
        bed for bed in room_snapshot["beds"] if bed["status"] == "AVAILABLE"
    ]
    assert available_beds, room_snapshot
    bed_id = int(available_beds[0]["id"])

    hold = response_data(
        request(
            "POST",
            f"/api/v1/student/batches/1/beds/{bed_id}/hold",
            token=student_token,
        )
    )
    hold_token = hold["token"]
    assert hold_token

    confirmed = response_data(
        request(
            "POST",
            f"/api/v1/student/batches/1/beds/{bed_id}/confirm",
            token=student_token,
            body={"token": hold_token},
        )
    )
    assert confirmed["assigned"] is True, confirmed

    assignment_result = response_data(
        request("GET", "/api/v1/student/batches/1/assignment", token=student_token)
    )
    assignment = assignment_result["assignment"]
    assignment_id = int(assignment["id"])
    assert int(assignment["bed_id"]) == bed_id, assignment

    admin_assignments = response_data(
        request("GET", "/api/v1/admin/batches/1/assignments", token=admin_token)
    )
    selected = next(
        item for item in admin_assignments if int(item["assignment_id"]) == assignment_id
    )
    assert selected["availableBeds"], selected
    target_bed_id = int(selected["availableBeds"][0]["bed_id"])

    adjusted = response_data(
        request(
            "POST",
            f"/api/v1/admin/assignments/{assignment_id}/adjust",
            token=admin_token,
            body={
                "newBedId": target_bed_id,
                "reason": "第一阶段自动化验收调整",
            },
        )
    )
    assert int(adjusted["oldBedId"]) == bed_id, adjusted
    assert int(adjusted["newBedId"]) == target_bed_id, adjusted

    after_adjustment = response_data(
        request("GET", "/api/v1/student/batches/1/assignment", token=student_token)
    )
    assert int(after_adjustment["assignment"]["bed_id"]) == target_bed_id

    audit_logs = response_data(
        request("GET", "/api/v1/admin/audit-logs?limit=100", token=admin_token)
    )
    actions = {log["action_type"] for log in audit_logs}
    assert "BED_ASSIGN_SELF" in actions, actions
    assert "ASSIGNMENT_ADJUST" in actions, actions

    request("POST", "/api/v1/auth/logout", token=student_token)
    request("POST", "/api/v1/auth/logout", token=admin_token)
    print("Phase 1 HTTP smoke flow passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exception:
        print(f"Phase 1 HTTP smoke flow failed: {exception}", file=sys.stderr)
        raise
