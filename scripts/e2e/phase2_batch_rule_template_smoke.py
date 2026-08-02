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
        "X-Request-Id": "phase2-batch-rule-template-smoke",
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
            raw = response.read()
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read()
    result = json.loads(raw.decode("utf-8")) if raw else None
    if status != expected_status:
        raise AssertionError(
            f"{method} {path}: expected {expected_status}, got {status}: {result}"
        )
    return result


def data(response: dict[str, Any]) -> Any:
    assert response["success"] is True, response
    return response["data"]


def error_code(response: dict[str, Any]) -> str:
    return str(response["error"]["code"])


def database_connection() -> pymysql.Connection:
    return pymysql.connect(
        host=os.environ.get("WUST_DORMITORY_DB_HOST", "127.0.0.1"),
        port=int(os.environ.get("WUST_DORMITORY_DB_PORT", "3306")),
        user=os.environ.get("WUST_DORMITORY_DB_USER", "wust_dormitory"),
        password=os.environ.get(
            "WUST_DORMITORY_DB_PASSWORD", "RuntimeTestPassword2026"
        ),
        database=os.environ.get("WUST_DORMITORY_DB_NAME", "wust_dormitory"),
        charset="utf8mb4",
        autocommit=True,
    )


def template_payload(
    code: str,
    name: str,
    *,
    make_default: bool,
    enabled: bool = True,
) -> dict[str, Any]:
    return {
        "ruleCode": code,
        "ruleName": name,
        "holdDurationSeconds": 480,
        "holdRenewalLimit": 2,
        "allowTeam": True,
        "teamMinSize": 2,
        "teamMaxSize": 5,
        "allowStudentRandom": True,
        "unselectedStrategy": "ADMIN_ALLOCATION",
        "ruleVersion": "phase2-template-smoke-v1",
        "enabled": enabled,
        "makeDefault": make_default,
        "changeReason": "批次规则模板真实接口验收",
    }


def main() -> int:
    admin_token = data(
        request(
            "POST",
            "/api/v1/auth/login",
            body={"username": "admin", "password": "Dormitory@2026"},
        )
    )["accessToken"]
    suffix = str(int(time.time() * 1000))[-10:]
    rule_code = f"RULE_{suffix}"
    disabled_code = f"OFF_{suffix}"
    explicit_batch_code = f"RULE-BATCH-{suffix}"
    default_batch_code = f"RULE-DEFAULT-{suffix}"
    copied_batch_code = f"RULE-COPY-{suffix}"
    connection = database_connection()

    try:
        initial_templates = data(
            request(
                "GET",
                "/api/v1/admin/batch-rule-templates",
                token=admin_token,
            )
        )
        assert any(bool(item["is_default"]) for item in initial_templates)

        revision_one = data(
            request(
                "POST",
                "/api/v1/admin/batch-rule-templates",
                token=admin_token,
                body=template_payload(
                    rule_code,
                    "规则模板验收修订一",
                    make_default=False,
                ),
            )
        )
        revision_one_id = int(revision_one["id"])
        assert int(revision_one["revision"]) == 1
        assert revision_one["rule_code"] == rule_code

        duplicate = request(
            "POST",
            "/api/v1/admin/batch-rule-templates",
            token=admin_token,
            body=template_payload(
                rule_code,
                "重复编码",
                make_default=False,
            ),
            expected_status=409,
        )
        assert error_code(duplicate) == "BATCH_RULE_TEMPLATE_CODE_CONFLICT"

        invalid_team = template_payload(
            f"BAD_{suffix}",
            "非法六人模板",
            make_default=False,
        )
        invalid_team["teamMaxSize"] = 6
        request(
            "POST",
            "/api/v1/admin/batch-rule-templates",
            token=admin_token,
            body=invalid_team,
            expected_status=400,
        )

        explicit_batch = data(
            request(
                "POST",
                "/api/v1/admin/batches",
                token=admin_token,
                body={
                    "batchCode": explicit_batch_code,
                    "batchName": "显式规则模板批次",
                    "startAt": "2027-05-01T08:00:00+08:00",
                    "endAt": "2027-05-31T18:00:00+08:00",
                    "ruleTemplateId": revision_one_id,
                },
            )
        )
        explicit_batch_id = int(explicit_batch["id"])
        assert int(explicit_batch["ruleTemplateId"]) == revision_one_id
        assert int(explicit_batch["holdDurationSeconds"]) == 480
        assert int(explicit_batch["teamMaxSize"]) == 5

        revision_two_payload = template_payload(
            rule_code,
            "规则模板验收修订二",
            make_default=True,
        )
        revision_two_payload.pop("ruleCode")
        revision_two_payload["holdDurationSeconds"] = 720
        revision_two_payload["holdRenewalLimit"] = 4
        revision_two_payload["allowStudentRandom"] = False
        revision_two_payload["ruleVersion"] = "phase2-template-smoke-v2"
        revision_two_payload["expectedVersion"] = int(revision_one["version"])
        revision_two = data(
            request(
                "POST",
                f"/api/v1/admin/batch-rule-templates/{revision_one_id}/revisions",
                token=admin_token,
                body=revision_two_payload,
            )
        )
        revision_two_id = int(revision_two["id"])
        assert int(revision_two["revision"]) == 2
        assert revision_two["is_default"] is True

        stale = request(
            "POST",
            f"/api/v1/admin/batch-rule-templates/{revision_one_id}/revisions",
            token=admin_token,
            body={**revision_two_payload, "expectedVersion": int(revision_one["version"])},
            expected_status=409,
        )
        assert error_code(stale) == "BATCH_RULE_TEMPLATE_VERSION_CONFLICT"

        default_batch = data(
            request(
                "POST",
                "/api/v1/admin/batches",
                token=admin_token,
                body={
                    "batchCode": default_batch_code,
                    "batchName": "默认规则模板批次",
                    "startAt": "2027-06-01T08:00:00+08:00",
                    "endAt": "2027-06-30T18:00:00+08:00",
                },
            )
        )
        default_batch_id = int(default_batch["id"])
        assert int(default_batch["ruleTemplateId"]) == revision_two_id
        assert int(default_batch["holdDurationSeconds"]) == 720
        assert int(default_batch["holdRenewalLimit"]) == 4
        assert default_batch["allowStudentRandom"] is False

        disabled_template = data(
            request(
                "POST",
                "/api/v1/admin/batch-rule-templates",
                token=admin_token,
                body=template_payload(
                    disabled_code,
                    "停用规则模板",
                    make_default=False,
                    enabled=False,
                ),
            )
        )
        disabled_batch = request(
            "POST",
            "/api/v1/admin/batches",
            token=admin_token,
            body={
                "batchCode": f"RULE-OFF-{suffix}",
                "batchName": "停用模板批次",
                "startAt": "2027-07-01T08:00:00+08:00",
                "endAt": "2027-07-31T18:00:00+08:00",
                "ruleTemplateId": int(disabled_template["id"]),
            },
            expected_status=409,
        )
        assert error_code(disabled_batch) == "BATCH_RULE_TEMPLATE_DISABLED"

        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT rule_template_id, hold_duration_seconds, hold_renewal_limit,
                       allow_student_random, rule_version
                FROM selection_batch WHERE id=%s
                """,
                (explicit_batch_id,),
            )
            assert cursor.fetchone() == (
                revision_one_id,
                480,
                2,
                1,
                "phase2-template-smoke-v1",
            )

            cursor.execute(
                """
                SELECT bed.id, room.id, building.id
                FROM bed
                JOIN room ON room.id=bed.room_id
                JOIN dormitory_floor floor ON floor.id=room.floor_id
                JOIN dormitory_building building ON building.id=floor.building_id
                WHERE building.enabled=1
                  AND room.operational_status='ENABLED'
                  AND bed.operational_status='ENABLED'
                ORDER BY bed.id
                LIMIT 1
                """
            )
            bed_id, room_id, building_id = map(int, cursor.fetchone())
            cursor.execute(
                "INSERT INTO batch_building_scope(batch_id, building_id) VALUES (%s, %s)",
                (explicit_batch_id, building_id),
            )
            cursor.execute(
                "INSERT INTO batch_room_scope(batch_id, room_id) VALUES (%s, %s)",
                (explicit_batch_id, room_id),
            )
            cursor.execute(
                "INSERT INTO batch_bed_scope(batch_id, bed_id) VALUES (%s, %s)",
                (explicit_batch_id, bed_id),
            )

        copied = data(
            request(
                "POST",
                f"/api/v1/admin/batches/{explicit_batch_id}/copy",
                token=admin_token,
                body={
                    "batchCode": copied_batch_code,
                    "batchName": "规则模板复制批次",
                    "startAt": "2027-08-01T08:00:00+08:00",
                    "endAt": "2027-08-31T18:00:00+08:00",
                    "reason": "验证复制保留精确模板修订和规则快照",
                },
            )
        )
        copied_batch_id = int(copied["id"])
        assert int(copied["ruleTemplateId"]) == revision_one_id

        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT rule_template_id, hold_duration_seconds, hold_renewal_limit,
                       allow_student_random, rule_version
                FROM selection_batch WHERE id=%s
                """,
                (copied_batch_id,),
            )
            assert cursor.fetchone() == (
                revision_one_id,
                480,
                2,
                1,
                "phase2-template-smoke-v1",
            )
            cursor.execute(
                """
                SELECT action_type, COUNT(*)
                FROM audit_log
                WHERE action_type IN (
                    'BATCH_RULE_TEMPLATE_CREATE',
                    'BATCH_RULE_TEMPLATE_REVISE',
                    'BATCH_CREATE',
                    'BATCH_COPY'
                )
                  AND occurred_at >= CURRENT_TIMESTAMP - INTERVAL 10 MINUTE
                GROUP BY action_type
                """
            )
            audit_counts = {row[0]: int(row[1]) for row in cursor.fetchall()}
            for action in (
                "BATCH_RULE_TEMPLATE_CREATE",
                "BATCH_RULE_TEMPLATE_REVISE",
                "BATCH_CREATE",
                "BATCH_COPY",
            ):
                assert audit_counts.get(action, 0) >= 1, audit_counts

        request("POST", "/api/v1/auth/logout", token=admin_token)
        print("Phase 2 batch rule template smoke flow passed")
        return 0
    finally:
        connection.close()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exception:
        print(
            f"Phase 2 batch rule template smoke flow failed: {exception}",
            file=sys.stderr,
        )
        raise
