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


def room_type_for_capacity(capacity: int) -> str:
    return {
        4: "FOUR_PERSON",
        5: "FIVE_PERSON",
        6: "SIX_PERSON",
    }.get(capacity, "OTHER")


def alternate_bed_type(current: str) -> str:
    return "LOFT_BED_DESK" if current.startswith("BUNK_") else "BUNK_UPPER"


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


def select_test_beds() -> tuple[int, int, int, int]:
    connection = database_connection()
    try:
        with connection.cursor() as cursor:
            cursor.execute("""
                SELECT bed.room_id, bed.id
                FROM bed
                JOIN room ON room.id=bed.room_id
                LEFT JOIN bed_assignment assignment ON assignment.bed_id=bed.id
                WHERE room.gender_restriction='M'
                  AND assignment.id IS NULL
                ORDER BY room.id, bed.position_index
                LIMIT 1
                """)
            empty_row = cursor.fetchone()
            assert empty_row is not None

            cursor.execute("""
                SELECT bed.room_id, bed.id
                FROM bed_assignment assignment
                JOIN bed ON bed.id=assignment.bed_id
                JOIN room ON room.id=bed.room_id
                WHERE room.gender_restriction='M'
                ORDER BY assignment.id
                LIMIT 1
                """)
            occupied_row = cursor.fetchone()
            assert occupied_row is not None
            return int(empty_row[0]), int(empty_row[1]), int(occupied_row[0]), int(occupied_row[1])
    finally:
        connection.close()


def payload_items(beds: list[dict[str, Any]], changed_bed_id: int | None = None) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for bed in beds:
        bed_id = int(bed["id"])
        x = float(bed["layout_x"])
        bed_type = str(bed["bed_type"])
        if bed_id == changed_bed_id:
            x = max(-5.2, min(5.2, x + 0.5))
            bed_type = alternate_bed_type(bed_type)
        result.append({
            "bedId": bed_id,
            "bedType": bed_type,
            "layoutX": x,
            "layoutZ": float(bed["layout_z"]),
            "rotationDegrees": int(bed["rotation_degrees"]),
        })
    return result


def main() -> int:
    admin_token = data(request(
        "POST",
        "/api/v1/auth/login",
        body={"username": "admin", "password": "Dormitory@2026"},
    ))["accessToken"]

    rooms = data(request("GET", "/api/v1/admin/rooms?gender=M", token=admin_token))
    assert rooms, rooms
    empty_room_id, empty_bed_id, occupied_room_id, occupied_bed_id = select_test_beds()
    room = next(item for item in rooms if int(item["id"]) == empty_room_id)

    exercise_room_capacity_update(admin_token, room)

    initial = data(request(
        "GET",
        f"/api/v1/admin/rooms/{empty_room_id}/bed-layout",
        token=admin_token,
    ))
    initial_version = int(initial["room"]["room_version"])
    beds = initial["beds"]
    assert len(beds) == int(initial["room"]["capacity"]), initial
    empty_bed = next(item for item in beds if int(item["id"]) == empty_bed_id)
    assert int(empty_bed["occupied"]) == 0, empty_bed
    original_type = str(empty_bed["bed_type"])

    custom_items = payload_items(beds, empty_bed_id)
    updated = data(request(
        "PUT",
        f"/api/v1/admin/rooms/{empty_room_id}/bed-layout",
        token=admin_token,
        body={
            "expectedRoomVersion": initial_version,
            "reason": "第二阶段自动化验收调整空床类型和布局",
            "beds": custom_items,
        },
    ))
    assert updated["layout_source"] == "CUSTOM_LAYOUT", updated
    assert int(updated["room"]["room_version"]) == initial_version + 1, updated
    assert updated["room"]["room_type"] == room_type_for_capacity(len(beds)), updated
    changed = next(item for item in updated["beds"] if int(item["id"]) == empty_bed_id)
    expected_item = next(item for item in custom_items if item["bedId"] == empty_bed_id)
    assert changed["bed_type"] == alternate_bed_type(original_type), changed
    assert abs(float(changed["layout_x"]) - float(expected_item["layoutX"])) < 0.001, changed

    conflict = request(
        "PUT",
        f"/api/v1/admin/rooms/{empty_room_id}/bed-layout",
        token=admin_token,
        body={
            "expectedRoomVersion": initial_version,
            "reason": "验证旧版本冲突",
            "beds": custom_items,
        },
        expected_status=409,
    )
    assert conflict["error"]["code"] == "ROOM_LAYOUT_VERSION_CONFLICT", conflict

    occupied_layout = data(request(
        "GET",
        f"/api/v1/admin/rooms/{occupied_room_id}/bed-layout",
        token=admin_token,
    ))
    occupied_bed = next(
        item for item in occupied_layout["beds"] if int(item["id"]) == occupied_bed_id
    )
    assert int(occupied_bed["occupied"]) == 1, occupied_bed
    occupied_items = payload_items(occupied_layout["beds"])
    for item in occupied_items:
        if item["bedId"] == occupied_bed_id:
            item["bedType"] = alternate_bed_type(str(occupied_bed["bed_type"]))
    occupied_conflict = request(
        "PUT",
        f"/api/v1/admin/rooms/{occupied_room_id}/bed-layout",
        token=admin_token,
        body={
            "expectedRoomVersion": int(occupied_layout["room"]["room_version"]),
            "reason": "验证非空床位类型不可修改",
            "beds": occupied_items,
        },
        expected_status=409,
    )
    assert occupied_conflict["error"]["code"] == "BED_TYPE_OCCUPIED", occupied_conflict

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
        f"/api/v1/student/batches/1/rooms/{empty_room_id}",
        token=student_token,
    ))
    student_bed = next(item for item in snapshot["beds"] if int(item["id"]) == empty_bed_id)
    assert student_bed["custom_layout"] is True, student_bed
    assert student_bed["bed_type"] == alternate_bed_type(original_type), student_bed
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
