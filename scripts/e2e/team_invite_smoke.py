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
    inviter_token = activate_and_login("202600000003", "测试男生003")
    invitee_token = activate_and_login("202600000004", "测试男生004")

    invitation_result = data(
        request(
            "POST",
            "/api/v1/student/team-invitations",
            token=inviter_token,
            body={"studentNumber": "202600000004"},
        )
    )
    assert invitation_result["invited"] is True, invitation_result
    assert invitation_result["studentNumber"] == "202600000004", invitation_result
    assert invitation_result["studentName"] == "测试男生004", invitation_result

    inviter_teams = data(request("GET", "/api/v1/student/teams", token=inviter_token))
    assert len(inviter_teams) == 1, inviter_teams
    forming_team = inviter_teams[0]
    assert_internal_identity_hidden(forming_team)
    assert forming_team["team_status"] == "FORMING", forming_team
    assert forming_team["member_role"] == "LEADER", forming_team
    assert int(forming_team["member_count"]) == 1, forming_team
    assert len(forming_team["members"]) == 2, forming_team
    member_statuses = {
        member["student_number"]: member["member_status"]
        for member in forming_team["members"]
    }
    assert member_statuses == {
        "202600000003": "JOINED",
        "202600000004": "INVITED",
    }, member_statuses

    invitee_teams_before = data(request("GET", "/api/v1/student/teams", token=invitee_token))
    assert invitee_teams_before == [], invitee_teams_before

    invitations = data(
        request("GET", "/api/v1/student/team-invitations", token=invitee_token)
    )
    assert len(invitations) == 1, invitations
    invitation = invitations[0]
    assert_internal_identity_hidden(invitation)
    assert invitation["inviter_name"] == "测试男生003", invitation
    assert invitation["inviter_student_number"] == "202600000003", invitation
    invitation_token = invitation["invitation_token"]

    request(
        "POST",
        "/api/v1/student/team-invitations/respond",
        token=invitee_token,
        body={"invitationToken": invitation_token, "accepted": True},
    )

    inviter_teams_after = data(request("GET", "/api/v1/student/teams", token=inviter_token))
    assert len(inviter_teams_after) == 1, inviter_teams_after
    accepted_team = inviter_teams_after[0]
    assert_internal_identity_hidden(accepted_team)
    assert int(accepted_team["member_count"]) == 2, accepted_team
    assert all(
        member["member_status"] == "JOINED"
        for member in accepted_team["members"]
    ), accepted_team

    invitee_teams_after = data(request("GET", "/api/v1/student/teams", token=invitee_token))
    assert len(invitee_teams_after) == 1, invitee_teams_after
    assert_internal_identity_hidden(invitee_teams_after[0])
    assert invitee_teams_after[0]["member_role"] == "MEMBER", invitee_teams_after

    internal_team_id = int(accepted_team["id"])
    request(
        "POST",
        f"/api/v1/student/teams/{internal_team_id}/lock",
        token=inviter_token,
    )
    locked_team = data(request("GET", "/api/v1/student/teams", token=inviter_token))[0]
    assert locked_team["team_status"] == "LOCKED", locked_team
    assert int(locked_team["member_count"]) == 2, locked_team
    assert_internal_identity_hidden(locked_team)

    request("POST", "/api/v1/auth/logout", token=invitee_token)
    request("POST", "/api/v1/auth/logout", token=inviter_token)
    print("Invitation-first team smoke flow passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exception:
        print(f"Invitation-first team smoke flow failed: {exception}", file=sys.stderr)
        raise
