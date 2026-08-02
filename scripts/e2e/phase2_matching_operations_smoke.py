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
    headers = {"Accept": "application/json", "X-Request-Id": "phase2-matching-smoke"}
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


def main() -> int:
    admin_token = data(request(
        "POST",
        "/api/v1/auth/login",
        body={"username": "admin", "password": "Dormitory@2026"},
    ))["accessToken"]

    request(
        "POST",
        "/api/v1/auth/activate",
        body={
            "studentNumber": "202600000020",
            "studentName": "测试男生020",
            "password": "StudentMatchingPassword2026",
        },
    )
    student_token = data(request(
        "POST",
        "/api/v1/auth/login",
        body={
            "username": "202600000020",
            "password": "StudentMatchingPassword2026",
        },
    ))["accessToken"]

    before_rooms = data(request(
        "GET",
        "/api/v1/student/batches/1/rooms",
        token=student_token,
    ))
    assert before_rooms, before_rooms
    before_by_id = {int(room["id"]): room for room in before_rooms}
    sample_before = before_rooms[0]
    for field in ("recommendationReasons", "conflictReasons", "dimensionCount"):
        assert field in sample_before, sample_before
    serialized_before = json.dumps(before_rooms, ensure_ascii=False)
    assert "feature_vector_json" not in serialized_before
    assert "questionnaire_answer" not in serialized_before

    schemes = data(request(
        "GET",
        "/api/v1/admin/matching-weight-schemes",
        token=admin_token,
    ))
    assert schemes, schemes
    source = next((scheme for scheme in schemes if bool(scheme["enabled"])), schemes[0])
    source_id = int(source["id"])
    source_revision = int(source["revision"])
    source_version = int(source["version"])
    weights = {key: float(value) for key, value in source["weights"].items()}
    rules = {key: float(value) for key, value in source["conflictRules"].items()}
    weights["sleepTimeMinutes"] = min(5.0, weights.get("sleepTimeMinutes", 1.2) + 0.2)

    created = data(request(
        "POST",
        f"/api/v1/admin/matching-weight-schemes/{source_id}/revisions",
        token=admin_token,
        body={
            "schemeName": f"{source['scheme_name']} 自动验收修订",
            "algorithmVersion": str(source["algorithm_version"]),
            "weights": weights,
            "conflictRules": rules,
            "activate": True,
            "expectedVersion": source_version,
            "reason": "第二阶段匹配权重不可变修订自动验收",
        },
    ))
    assert int(created["revision"]) == source_revision + 1, created
    assert bool(created["enabled"]), created

    conflict = request(
        "POST",
        f"/api/v1/admin/matching-weight-schemes/{source_id}/revisions",
        token=admin_token,
        body={
            "schemeName": "旧版本重复提交",
            "algorithmVersion": str(source["algorithm_version"]),
            "weights": weights,
            "conflictRules": rules,
            "activate": False,
            "expectedVersion": source_version,
            "reason": "验证旧版本冲突",
        },
        expected_status=409,
    )
    assert conflict["error"]["code"] == "MATCHING_SCHEME_VERSION_CONFLICT", conflict

    after_schemes = data(request(
        "GET",
        "/api/v1/admin/matching-weight-schemes",
        token=admin_token,
    ))
    assert sum(1 for scheme in after_schemes if bool(scheme["enabled"])) == 1, after_schemes

    after_rooms = data(request(
        "GET",
        "/api/v1/student/batches/1/rooms",
        token=student_token,
    ))
    after_by_id = {int(room["id"]): room for room in after_rooms}
    common_ids = set(before_by_id) & set(after_by_id)
    assert common_ids, (before_rooms, after_rooms)
    for room_id in list(common_ids)[:5]:
        assert float(before_by_id[room_id]["matchScore"]) == float(after_by_id[room_id]["matchScore"]), (
            before_by_id[room_id],
            after_by_id[room_id],
        )

    audits = data(request("GET", "/api/v1/admin/audit-logs?limit=200", token=admin_token))
    assert "MATCHING_SCHEME_REVISION_CREATE" in {row["action_type"] for row in audits}, audits

    request("POST", "/api/v1/auth/logout", token=student_token)
    request("POST", "/api/v1/auth/logout", token=admin_token)
    print("Phase 2 matching operations smoke flow passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exception:
        print(f"Phase 2 matching operations smoke flow failed: {exception}", file=sys.stderr)
        raise
