#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from typing import Any

import pymysql

BASE_URL = os.environ.get("WUST_DORMITORY_BASE_URL", "http://127.0.0.1:8080").rstrip("/")


def request(
    method: str,
    path: str,
    *,
    token: str | None = None,
    body: Any | None = None,
    expected_status: int = 200,
) -> Any:
    headers = {"Accept": "application/json", "X-Request-Id": "phase2-layout-smoke"}
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
    result = json.loads(raw.decode("utf-8")) if raw else None
    if status != expected_status:
        raise AssertionError(f"{method} {path}: expected {expected_status}, got {status}: {result}")
    return result


def data(response: dict[str, Any]) -> Any:
    assert response["success"] is True, response
    return response["data"]


def database_connection() -> pymysql.Connection:
    return pymysql.connect(
        host=os.environ.get("WUST_DORMITORY_DB_HOST", "127.0.0.1"),
        port=int(os.environ.get("WUST_DORMITORY_DB_PORT", "3306")),
        user=os.environ.get("WUST_DORMITORY_DB_USER", "wust_dormitory"),
        password=os.environ.get("WUST_DORMITORY_DB_PASSWORD", "RuntimeTestPassword2026"),
        database=os.environ.get("WUST_DORMITORY_DB_NAME", "wust_dormitory"),
        charset="utf8mb4",
        autocommit=True,
    )


def exercise_room_capacity_update(
    admin_token: str,
    room: dict[str, Any],
) -> None:
    room_id = int(room["id"])
    physical_bed_count = int(room["bed_count"])
    assert physical_bed_count > 1, room

    connection = database_connection()
    original_status: str | None = None
    bed_id: int | None = None
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT id, operational_status FROM bed WHERE room_id=%s ORDER BY position_index LIMIT 1",
                (room_id,),
            )
            row = cursor.fetchone()
            assert row is not None, room
            bed_id = int(row[0])
            original_status = str(row[1])
            cursor.execute(
                "UPDATE bed SET operational_status='MAINTENANCE' WHERE id=%s",
                (bed_id,),
            )

        refreshed_rooms = data(request(
            "GET",
            f"/api/v1/admin/rooms?gender={room['gender_restriction']}",
            token=admin_token,
        ))
        refreshed = next(item for item in refreshed_rooms if int(item["id"]) == room_id)
        assert int(refreshed["bed_count"]) == physical_bed_count, refreshed
        assert int(refreshed["enabled_bed_count"]) == physical_bed_count - 1, refreshed

        request(
            "PUT",
            f"/api/v1/admin/rooms/{room_id}",
            token=admin_token,
            body={
                "roomType": room["room_type"],
                "capacity": physical_bed_count,
                "gender": room["gender_restriction"],
                "operationalStatus": room["operational_status"],
                "remark": "自动化验证：维护床位不改变物理容量",
                "reason": "验证房间容量按物理床位总数计算",
            },
        )
    finally:
        if bed_id is not None and original_status is not None:
            with connection.cursor() as cursor:
                cursor.execute(
                    "UPDATE bed SET operational_status=%s WHERE id=%s",
                    (original_status, bed_id),
                )
        connection.close()


def main() -> int:
    admin_token = data(request(
        "POST",
        "/api/v1/auth/login",
        body={"username": "admin", "password": "Dormitory@2026"},
    ))["accessToken"]

    rooms = data(request("GET", "/api/v1/admin/rooms?gender=M", token=admin_token))
    assert rooms, rooms
    room = rooms[0]
    room_id = int(room["id"])

    exercise_room_capacity_update(admin_token, room)

    initial = data(request(
        "GET",
        f"/api/v1/admin/rooms/{room_id}/bed-layout",
        token=admin_token,
    ))
    assert initial["layout_source"] == "DEFAULT_LAYOUT", initial
    initial_version = int(initial["room"]["room_version"])
    beds = initial["beds"]
    assert len(beds) == int(initial["room"]["capacity"]), initial

    custom_items: list[dict[str, Any]] = []
    changed_bed_id: int | None = None
    for bed in beds:
        x = float(bed["layout_x"])
        z = float(bed["layout_z"])
        if changed_bed_id is None and not str(bed["bed_type"]).startswith("BUNK_"):
            x = max(-5.2, min(5.2, x + 0.5))
            changed_bed_id = int(bed["id"])
        custom_items.append({
            "bedId": int(bed["id"]),
            "layoutX": x,
            "layoutZ": z,
            "rotationDegrees": int(bed["rotation_degrees"]),
        })
    assert changed_bed_id is not None

    updated = data(request(
        "PUT",
        f"/api/v1/admin/rooms/{room_id}/bed-layout",
        token=admin_token,
        body={
            "expectedRoomVersion": initial_version,
            "reason": "第二阶段自动化验收调整房间布局",
            "beds": custom_items,
        },
    ))
    assert updated["layout_source"] == "CUSTOM_LAYOUT", updated
    assert int(updated["room"]["room_version"]) == initial_version + 1, updated
    changed = next(item for item in updated["beds"] if int(item["id"]) == changed_bed_id)
    expected_x = next(item["layoutX"] for item in custom_items if item["bedId"] == changed_bed_id)
    assert abs(float(changed["layout_x"]) - expected_x) < 0.001, changed

    conflict = request(
        "PUT",
        f"/api/v1/admin/rooms/{room_id}/bed-layout",
        token=admin_token,
        body={
            "expectedRoomVersion": initial_version,
            "reason": "验证旧版本冲突",
            "beds": custom_items,
        },
        expected_status=409,
    )
    assert conflict["error"]["code"] == "ROOM_LAYOUT_VERSION_CONFLICT", conflict

    request(
        "POST",
        "/api/v1/auth/activate",
        body={
            "studentNumber": "202600000010",
            "studentName": "测试男生010",
            "password": "StudentLayoutPassword2026",
        },
    )
    student_token = data(request(
        "POST",
        "/api/v1/auth/login",
        body={
            "username": "202600000010",
            "password": "StudentLayoutPassword2026",
        },
    ))["accessToken"]

    snapshot = data(request(
        "GET",
        f"/api/v1/student/batches/1/rooms/{room_id}",
        token=student_token,
    ))
    student_bed = next(item for item in snapshot["beds"] if int(item["id"]) == changed_bed_id)
    assert student_bed["custom_layout"] is True, student_bed
    assert abs(float(student_bed["layout_x"]) - expected_x) < 0.001, student_bed
    assert int(student_bed["rotation_degrees"]) in {0, 90, 180, 270}, student_bed

    audits = data(request("GET", "/api/v1/admin/audit-logs?limit=100", token=admin_token))
    actions = {row["action_type"] for row in audits}
    assert "ROOM_UPDATE" in actions, audits
    assert "ROOM_LAYOUT_UPDATE" in actions, audits

    request("POST", "/api/v1/auth/logout", token=student_token)
    request("POST", "/api/v1/auth/logout", token=admin_token)
    print("Phase 2 room layout smoke flow passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exception:
        print(f"Phase 2 room layout smoke flow failed: {exception}", file=sys.stderr)
        raise
