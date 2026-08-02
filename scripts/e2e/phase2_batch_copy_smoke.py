#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from typing import Any

import pymysql

BASE_URL = os.environ.get("WUST_DORMITORY_BASE_URL", "http://127.0.0.1:8080").rstrip("/")


def request(method: str, path: str, *, token: str | None = None,
            body: Any | None = None, expected_status: int = 200) -> Any:
    headers = {"Accept": "application/json", "X-Request-Id": "phase2-batch-copy-smoke"}
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


def error_code(response: dict[str, Any]) -> str:
    return str(response["error"]["code"])


def main() -> int:
    admin_token = data(request(
        "POST", "/api/v1/auth/login",
        body={"username": "admin", "password": "Dormitory@2026"},
    ))["accessToken"]
    suffix = str(int(time.time() * 1000))[-10:]
    source_code = f"COPY-SRC-{suffix}"
    copy_code = f"COPY-NEW-{suffix}"
    connection = database_connection()
    selected_bed_id: int | None = None
    original_bed_status: str | None = None
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT id FROM app_user WHERE username='admin'")
            admin_id = int(cursor.fetchone()[0])
            cursor.execute("SELECT id FROM questionnaire_version ORDER BY id LIMIT 1")
            questionnaire_id = int(cursor.fetchone()[0])
            cursor.execute("SELECT id FROM matching_weight_scheme ORDER BY id LIMIT 1")
            scheme_id = int(cursor.fetchone()[0])
            cursor.execute("""
                SELECT bed.id, bed.operational_status, r.id, b.id
                FROM bed JOIN room r ON r.id=bed.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE b.enabled=1 AND r.operational_status='ENABLED'
                  AND bed.operational_status='ENABLED'
                ORDER BY bed.id LIMIT 1
            """)
            selected_bed_id, original_bed_status, room_id, building_id = cursor.fetchone()
            selected_bed_id = int(selected_bed_id)
            cursor.execute("SELECT id FROM student ORDER BY id LIMIT 1")
            student_id = int(cursor.fetchone()[0])
            cursor.execute("""
                INSERT INTO selection_batch
                (batch_code, batch_name, batch_status, questionnaire_version_id,
                 matching_weight_scheme_id, start_at, end_at, hold_duration_seconds,
                 hold_renewal_limit, allow_team, team_min_size, team_max_size,
                 allow_student_random, unselected_strategy, rule_version, created_by)
                VALUES (%s, '批次复制自动验收来源', 'FINISHED', %s, %s,
                        '2026-01-01 08:00:00', '2026-01-31 18:00:00',
                        420, 3, 1, 2, 4, 1, 'ADMIN_ALLOCATION',
                        'phase2-copy-smoke-v1', %s)
            """, (source_code, questionnaire_id, scheme_id, admin_id))
            source_batch_id = int(cursor.lastrowid)
            cursor.execute("INSERT INTO batch_building_scope(batch_id, building_id) VALUES (%s, %s)", (source_batch_id, building_id))
            cursor.execute("INSERT INTO batch_room_scope(batch_id, room_id) VALUES (%s, %s)", (source_batch_id, room_id))
            cursor.execute("INSERT INTO batch_bed_scope(batch_id, bed_id) VALUES (%s, %s)", (source_batch_id, selected_bed_id))
            cursor.execute("INSERT INTO batch_student_eligibility(batch_id, student_id, eligibility_status) VALUES (%s, %s, 'ELIGIBLE')", (source_batch_id, student_id))

        payload = {
            "batchCode": copy_code,
            "batchName": "批次复制自动验收副本",
            "startAt": "2027-03-01T08:00:00+08:00",
            "endAt": "2027-03-31T18:00:00+08:00",
            "reason": "验证完整配置模板复制且不复制运行事实",
        }
        copied = data(request("POST", f"/api/v1/admin/batches/{source_batch_id}/copy", token=admin_token, body=payload))
        copied_batch_id = int(copied["id"])
        assert copied["batchStatus"] == "DRAFT", copied
        assert (int(copied["buildingScopeCount"]), int(copied["roomScopeCount"]), int(copied["bedScopeCount"])) == (1, 1, 1)

        with connection.cursor() as cursor:
            cursor.execute("""
                SELECT batch_status, questionnaire_version_id, matching_weight_scheme_id,
                       hold_duration_seconds, hold_renewal_limit, allow_team,
                       team_min_size, team_max_size, allow_student_random,
                       unselected_strategy, rule_version, published_at
                FROM selection_batch WHERE id=%s
            """, (copied_batch_id,))
            assert cursor.fetchone() == ('DRAFT', questionnaire_id, scheme_id, 420, 3, 1, 2, 4, 1, 'ADMIN_ALLOCATION', 'phase2-copy-smoke-v1', None)
            for table in ("batch_building_scope", "batch_room_scope", "batch_bed_scope"):
                cursor.execute(f"SELECT COUNT(*) FROM {table} WHERE batch_id=%s", (copied_batch_id,))
                assert cursor.fetchone()[0] == 1, table
            for table in ("batch_student_eligibility", "selection_team", "bed_assignment", "active_batch_student_lock", "allocation_run"):
                cursor.execute(f"SELECT COUNT(*) FROM {table} WHERE batch_id=%s", (copied_batch_id,))
                assert cursor.fetchone()[0] == 0, table
            cursor.execute("SELECT COUNT(*) FROM audit_log WHERE action_type='BATCH_COPY' AND resource_id=%s", (copied_batch_id,))
            assert cursor.fetchone()[0] == 1

        duplicate = request("POST", f"/api/v1/admin/batches/{source_batch_id}/copy", token=admin_token, body=payload, expected_status=409)
        assert error_code(duplicate) == "BATCH_CODE_CONFLICT", duplicate

        invalid = dict(payload, batchCode=f"COPY-BAD-{suffix}", startAt="2027-04-10T08:00:00+08:00", endAt="2027-04-01T08:00:00+08:00")
        invalid_response = request("POST", f"/api/v1/admin/batches/{source_batch_id}/copy", token=admin_token, body=invalid, expected_status=400)
        assert error_code(invalid_response) == "BATCH_TIME_INVALID", invalid_response

        with connection.cursor() as cursor:
            cursor.execute("UPDATE bed SET operational_status='MAINTENANCE' WHERE id=%s", (selected_bed_id,))
        unavailable_payload = dict(payload, batchCode=f"COPY-UNAV-{suffix}")
        unavailable = request("POST", f"/api/v1/admin/batches/{source_batch_id}/copy", token=admin_token, body=unavailable_payload, expected_status=409)
        assert error_code(unavailable) == "BATCH_COPY_RESOURCE_UNAVAILABLE", unavailable
        with connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) FROM selection_batch WHERE batch_code=%s", (unavailable_payload["batchCode"],))
            assert cursor.fetchone()[0] == 0
            cursor.execute("UPDATE bed SET operational_status=%s WHERE id=%s", (original_bed_status, selected_bed_id))
            cursor.execute("""
                INSERT INTO selection_batch
                (batch_code, batch_name, batch_status, questionnaire_version_id,
                 matching_weight_scheme_id, start_at, end_at, hold_duration_seconds,
                 hold_renewal_limit, allow_team, team_min_size, team_max_size,
                 allow_student_random, unselected_strategy, rule_version, created_by)
                SELECT %s, '已取消复制来源', 'CANCELLED', questionnaire_version_id,
                       matching_weight_scheme_id, start_at, end_at, hold_duration_seconds,
                       hold_renewal_limit, allow_team, team_min_size, team_max_size,
                       allow_student_random, unselected_strategy, rule_version, created_by
                FROM selection_batch WHERE id=%s
            """, (f"COPY-CAN-{suffix}", source_batch_id))
            cancelled_batch_id = int(cursor.lastrowid)

        cancelled_payload = dict(payload, batchCode=f"COPY-FROM-CAN-{suffix}")
        cancelled = request("POST", f"/api/v1/admin/batches/{cancelled_batch_id}/copy", token=admin_token, body=cancelled_payload, expected_status=400)
        assert error_code(cancelled) == "BATCH_COPY_CANCELLED_FORBIDDEN", cancelled
        request("POST", "/api/v1/auth/logout", token=admin_token)
        print("Phase 2 batch copy smoke flow passed")
        return 0
    finally:
        if selected_bed_id is not None and original_bed_status is not None:
            with connection.cursor() as cursor:
                cursor.execute("UPDATE bed SET operational_status=%s WHERE id=%s", (original_bed_status, selected_bed_id))
        connection.close()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exception:
        print(f"Phase 2 batch copy smoke flow failed: {exception}", file=sys.stderr)
        raise
