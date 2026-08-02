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
        "X-Request-Id": f"team-invite-smoke-{path.replace('/', '-')[:36]}",
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
            content_type = response.headers.get("Content-Type", "")
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read()
        content_type = error.headers.get("Content-Type", "")

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
    if path.startswith("/api/") and expected_status < 400:
        assert result.get("success") is True, result
        assert result.get("requestId"), result
    return result


def data(response: dict[str, Any]) -> Any:
    assert "data" in response, response
    return response["data"]


def activate_and_login(student_number: str, student_name: str) -> str:
    password = "TeamSmokePassword2026"
    request(
        "POST",
        "/api/v1/auth/activate",
        body={
            "studentNumber": student_number,
            "studentName": student_name,
            "password": password,
        },
    )
    login = request(
        "POST",
        "/api/v1/auth/login",
        body={"username": student_number, "password": password},
    )
    token = data(login)["accessToken"]
    assert token
    return token


def assert_internal_identity_hidden(team: dict[str, Any]) -> None:
    forbidden = {"team_name", "team_code", "teamName", "teamCode"}
    assert forbidden.isdisjoint(team), team


def main() -> int:
    student_names = {
        "202600000003": "测试男生003",
        "202600000004": "测试男生004",
        "202600000005": "测试男生005",
        "202600000006": "测试男生006",
        "202600000007": "测试男生007",
        "202600000008": "测试男生008",
    }
    tokens = {
        number: activate_and_login(number, name)
        for number, name in student_names.items()
    }
    inviter_token = tokens["202600000003"]
    accepted_token = tokens["202600000004"]

    for invitee_number in (
        "202600000004",
        "202600000005",
        "202600000006",
        "202600000007",
    ):
        invitation_result = data(request(
            "POST",
            "/api/v1/student/team-invitations",
            token=inviter_token,
            body={"studentNumber": invitee_number},
        ))
        assert invitation_result["invited"] is True, invitation_result
        assert invitation_result["studentNumber"] == invitee_number, invitation_result

    size_limit = request(
        "POST",
        "/api/v1/student/team-invitations",
        token=inviter_token,
        body={"studentNumber": "202600000008"},
        expected_status=409,
    )
    assert size_limit["error"]["code"] == "TEAM_SIZE_LIMIT", size_limit

    inviter_teams = data(request("GET", "/api/v1/student/teams", token=inviter_token))
    assert len(inviter_teams) == 1, inviter_teams
    forming_team = inviter_teams[0]
    assert_internal_identity_hidden(forming_team)
    assert forming_team["team_status"] == "FORMING", forming_team
    assert int(forming_team["confirmed_member_count"]) == 1, forming_team
    assert int(forming_team["pending_invitation_count"]) == 4, forming_team
    assert len(forming_team["members"]) == 5, forming_team

    accepted_invitations = data(request(
        "GET", "/api/v1/student/team-invitations", token=accepted_token
    ))
    assert len(accepted_invitations) == 1, accepted_invitations
    accepted_invitation = accepted_invitations[0]
    request(
        "POST",
        "/api/v1/student/team-invitations/respond",
        token=accepted_token,
        body={
            "invitationToken": accepted_invitation["invitation_token"],
            "accepted": True,
        },
    )

    accepted_team = data(request("GET", "/api/v1/student/teams", token=inviter_token))[0]
    assert int(accepted_team["confirmed_member_count"]) == 2, accepted_team
    assert int(accepted_team["pending_invitation_count"]) == 3, accepted_team

    team_id = int(accepted_team["id"])
    lock_result = data(request(
        "POST",
        f"/api/v1/student/teams/{team_id}/lock",
        token=inviter_token,
    ))
    assert int(lock_result["memberCount"]) == 2, lock_result
    assert int(lock_result["invalidatedInvitationCount"]) == 3, lock_result

    locked_team = data(request("GET", "/api/v1/student/teams", token=inviter_token))[0]
    assert locked_team["team_status"] == "LOCKED", locked_team
    assert int(locked_team["confirmed_member_count"]) == 2, locked_team
    assert int(locked_team["pending_invitation_count"]) == 0, locked_team
    assert len(locked_team["members"]) == 2, locked_team

    for pending_number in ("202600000005", "202600000006", "202600000007"):
        pending_token = tokens[pending_number]
        pending_invitations = data(request(
            "GET", "/api/v1/student/team-invitations", token=pending_token
        ))
        assert pending_invitations == [], pending_invitations
        pending_notifications = data(request(
            "GET", "/api/v1/student/notifications", token=pending_token
        ))
        assert any(
            item["notification_type"] == "TEAM_INVITATION_CANCELLED"
            for item in pending_notifications
        ), pending_notifications

    accepted_member = next(
        member for member in locked_team["members"]
        if member["student_number"] == "202600000004"
    )
    remove_result = data(request(
        "DELETE",
        f"/api/v1/student/teams/{team_id}/members/{int(accepted_member['student_id'])}",
        token=inviter_token,
    ))
    assert remove_result["removed"] is True, remove_result

    accepted_teams_after_remove = data(request(
        "GET", "/api/v1/student/teams", token=accepted_token
    ))
    assert accepted_teams_after_remove == [], accepted_teams_after_remove
    accepted_notifications = data(request(
        "GET", "/api/v1/student/notifications", token=accepted_token
    ))
    assert any(
        item["notification_type"] == "TEAM_MEMBER_REMOVED"
        for item in accepted_notifications
    ), accepted_notifications

    personal_prepare = data(request(
        "POST",
        "/api/v1/student/batches/1/personal-selection/prepare",
        token=inviter_token,
    ))
    assert personal_prepare["leftTeam"] is True, personal_prepare
    assert personal_prepare["dissolved"] is True, personal_prepare
    assert data(request("GET", "/api/v1/student/teams", token=inviter_token)) == []

    for token in tokens.values():
        request("POST", "/api/v1/auth/logout", token=token)
    print("Five-member invitation, invalidation, removal and leave smoke flow passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exception:
        print(f"Team invitation smoke flow failed: {exception}", file=sys.stderr)
        raise
