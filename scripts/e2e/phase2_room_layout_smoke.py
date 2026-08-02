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
    return {4: "FOUR_PERSON", 5: "FIVE_PERSON", 6: "SIX_PERSON"}.get(capacity, "OTHER")


def exercise_room_capacity_update(admin_token: str, room: dict[str, Any]) -> None:
    room_id = int(room["id"])
    physical_bed_count = int(room["bed_count"])
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
            cursor.execute("UPDATE bed SET operational_status='MAINTENANCE' WHERE id=%s", (bed_id,))

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
                cursor.execute("UPDATE bed SET operational_status=%s WHERE id=%s", (original_status, bed_id))
        connection.close()


def unit_payload(layout: dict[str, Any], change_bed_id: int | None = None) -> list[dict[str, Any]]:
    beds = layout["beds"]
    frame_groups: dict[int, list[dict[str, Any]]] = {}
    units: list[list[dict[str, Any]]] = []
    for bed in beds:
        frame_id = bed.get("bed_frame_id")
        if frame_id is None:
            units.append([bed])
        else:
            frame_groups.setdefault(int(frame_id), []).append(bed)
    units.extend(frame_groups.values())

    result: list[dict[str, Any]] = []
    for group in units:
        representative = next(
            (bed for bed in group if bed["bed_type"] == "BUNK_UPPER"),
            sorted(group, key=lambda item: int(item["position_index"]))[0],
        )
        unit_type = "BUNK" if representative.get("bed_frame_id") is not None else "LOFT_BED_DESK"
        x = float(representative["layout_x"])
        if int(representative["id"]) == change_bed_id:
            x = max(-5.2, min(5.2, x + 0.5))
            unit_type = "BUNK"
        result.append({
            "bedId": int(representative["id"]),
            "bedType": unit_type,
            "layoutX": x,
            "layoutZ": float(representative["layout_z"]),
            "rotationDegrees": int(representative["rotation_degrees"]),
        })
    return result


def choose_empty_loft_and_occupied_bed() -> tuple[int, int, int, int]:
    connection = database_connection()
    try:
        with connection.cursor() as cursor:
            cursor.execute("""
                SELECT bed.room_id, bed.id
                FROM bed
                JOIN room ON room.id=bed.room_id
                LEFT JOIN bed_assignment assignment ON assignment.bed_id=bed.id
                WHERE room.gender_restriction='M'
                  AND room.capacity < 8
                  AND bed.bed_type='LOFT_BED_DESK'
                  AND assignment.id IS NULL
                ORDER BY room.id, bed.position_index
                LIMIT 1
                """)
            empty_row = cursor.fetchone()
            assert empty_row is not None, "No empty LOFT_BED_DESK bed found in male rooms"

            # Ensure the empty loft bed belongs to the batch scope so that the split
            # propagation in RoomLayoutService can copy its scope entries to the new bed.
            cursor.execute(
                "INSERT IGNORE INTO batch_bed_scope (batch_id, bed_id) VALUES (1, %s)",
                (int(empty_row[1]),),
            )

            # Try to find an existing occupied bed; if none, create a temporary assignment.
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
            if occupied_row is None:
                # Find a different bed in a different room to temporarily occupy.
                empty_room_id = int(empty_row[0])
                cursor.execute("""
                    SELECT bed.room_id, bed.id, student.id
                    FROM bed
                    JOIN room ON room.id=bed.room_id
                    CROSS JOIN (
                        SELECT id FROM student
                        WHERE gender='M'
                        LIMIT 1
                    ) student
                    LEFT JOIN bed_assignment assignment ON assignment.bed_id=bed.id
                    WHERE room.gender_restriction='M'
                      AND room.id <> %s
                      AND assignment.id IS NULL
                    ORDER BY room.id, bed.position_index
                    LIMIT 1
                    """, (empty_room_id,))
                temp_row = cursor.fetchone()
                assert temp_row is not None, "No empty bed available for temporary assignment"
                oe_room_id, oe_bed_id, temp_student_id = int(temp_row[0]), int(temp_row[1]), int(temp_row[2])
                cursor.execute(
                    "INSERT INTO bed_assignment (batch_id, student_id, bed_id, assignment_method, assigned_by, assigned_at) "
                    "VALUES (1, %s, %s, 'MANUAL_ADJUSTMENT', 1, NOW())",
                    (temp_student_id, oe_bed_id),
                )
                occupied_row = (oe_room_id, oe_bed_id)

            assert occupied_row is not None
            return int(empty_row[0]), int(empty_row[1]), int(occupied_row[0]), int(occupied_row[1])
    finally:
        connection.close()


def main() -> int:
    admin_token = data(request(
        "POST", "/api/v1/auth/login",
        body={"username": "admin", "password": "Dormitory@2026"},
    ))["accessToken"]

    rooms = data(request("GET", "/api/v1/admin/rooms?gender=M", token=admin_token))
    empty_room_id, empty_loft_id, occupied_room_id, occupied_bed_id = choose_empty_loft_and_occupied_bed()
    room = next(item for item in rooms if int(item["id"]) == empty_room_id)
    exercise_room_capacity_update(admin_token, room)

    initial = data(request(
        "GET", f"/api/v1/admin/rooms/{empty_room_id}/bed-layout", token=admin_token
    ))
    initial_version = int(initial["room"]["room_version"])
    initial_capacity = int(initial["room"]["capacity"])
    empty_loft = next(item for item in initial["beds"] if int(item["id"]) == empty_loft_id)
    assert empty_loft["bed_type"] == "LOFT_BED_DESK", empty_loft
    assert int(empty_loft["occupied"]) == 0, empty_loft

    custom_items = unit_payload(initial, empty_loft_id)
    updated = data(request(
        "PUT", f"/api/v1/admin/rooms/{empty_room_id}/bed-layout",
        token=admin_token,
        body={
            "expectedRoomVersion": initial_version,
            "reason": "自动化验收：将空上床下桌拆分为上下铺",
            "beds": custom_items,
        },
    ))
    assert updated["layout_source"] == "CUSTOM_LAYOUT", updated
    assert int(updated["room"]["room_version"]) == initial_version + 1, updated
    assert int(updated["room"]["capacity"]) == initial_capacity + 1, updated
    assert updated["room"]["room_type"] == room_type_for_capacity(initial_capacity + 1), updated
    assert len(updated["beds"]) == len(initial["beds"]) + 1, updated

    original_after = next(item for item in updated["beds"] if int(item["id"]) == empty_loft_id)
    assert original_after["bed_type"] == "BUNK_UPPER", original_after
    frame_id = int(original_after["bed_frame_id"])
    lower_beds = [
        item for item in updated["beds"]
        if item["bed_type"] == "BUNK_LOWER" and int(item["bed_frame_id"]) == frame_id
    ]
    assert len(lower_beds) == 1, updated
    lower_bed = lower_beds[0]
    assert abs(float(lower_bed["layout_x"]) - float(original_after["layout_x"])) < 0.001
    assert abs(float(lower_bed["layout_z"]) - float(original_after["layout_z"])) < 0.001

    connection = database_connection()
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT COUNT(*)
                FROM batch_bed_scope target
                JOIN batch_bed_scope source ON source.batch_id=target.batch_id
                WHERE source.bed_id=%s AND target.bed_id=%s
                """,
                (empty_loft_id, int(lower_bed["id"])),
            )
            assert int(cursor.fetchone()[0]) > 0
    finally:
        connection.close()

    version_conflict = request(
        "PUT", f"/api/v1/admin/rooms/{empty_room_id}/bed-layout",
        token=admin_token,
        body={
            "expectedRoomVersion": initial_version,
            "reason": "验证旧版本冲突",
            "beds": custom_items,
        },
        expected_status=409,
    )
    assert version_conflict["error"]["code"] == "ROOM_LAYOUT_VERSION_CONFLICT", version_conflict

    occupied_layout = data(request(
        "GET", f"/api/v1/admin/rooms/{occupied_room_id}/bed-layout", token=admin_token
    ))
    occupied = next(item for item in occupied_layout["beds"] if int(item["id"]) == occupied_bed_id)
    assert int(occupied["occupied"]) == 1, occupied
    occupied_items = unit_payload(occupied_layout)
    occupied_unit = next(
        item for item in occupied_items
        if item["bedId"] == occupied_bed_id
        or any(
            int(bed["id"]) == occupied_bed_id
            and bed.get("bed_frame_id") is not None
            and item["bedType"] == "BUNK"
            for bed in occupied_layout["beds"]
        )
    )
    occupied_unit["bedType"] = "LOFT_BED_DESK" if occupied_unit["bedType"] == "BUNK" else "BUNK"
    occupied_conflict = request(
        "PUT", f"/api/v1/admin/rooms/{occupied_room_id}/bed-layout",
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
        "POST", "/api/v1/auth/activate",
        body={
            "studentNumber": "202600000017",
            "studentName": "刘宇航",
            "password": "StudentLayoutPassword2026",
        },
    )
    student_token = data(request(
        "POST", "/api/v1/auth/login",
        body={"username": "202600000017", "password": "StudentLayoutPassword2026"},
    ))["accessToken"]

    snapshot = data(request(
        "GET", f"/api/v1/student/batches/1/rooms/{empty_room_id}", token=student_token
    ))
    student_upper = next(item for item in snapshot["beds"] if int(item["id"]) == empty_loft_id)
    student_lower = next(item for item in snapshot["beds"] if int(item["id"]) == int(lower_bed["id"]))
    assert student_upper["bed_type"] == "BUNK_UPPER", student_upper
    assert student_lower["bed_type"] == "BUNK_LOWER", student_lower
    assert student_upper["custom_layout"] is True, student_upper
    assert student_lower["custom_layout"] is True, student_lower

    audits = data(request("GET", "/api/v1/admin/audit-logs?limit=100", token=admin_token))
    actions = {row["action_type"] for row in audits}
    assert "ROOM_UPDATE" in actions, audits
    assert "ROOM_LAYOUT_UPDATE" in actions, audits

    request("POST", "/api/v1/auth/logout", token=student_token)
    request("POST", "/api/v1/auth/logout", token=admin_token)
    print("Room layout split and capacity smoke flow passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exception:
        print(f"Phase 2 room layout smoke flow failed: {exception}", file=sys.stderr)
        raise
