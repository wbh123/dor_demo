#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

BASE_URL = os.environ.get('BASE_URL', 'http://localhost:8080').rstrip('/')
ADMIN_TOKEN = os.environ.get('ADMIN_TOKEN', '').strip()
SUMMARY = Path(os.environ.get('K6_SUMMARY', 'scripts/load/generated/k6-summary.json'))
FIXTURES = Path(os.environ.get('LOAD_FIXTURES', 'scripts/load/generated/selection-fixtures.json'))
LEASE_SETTLE_SECONDS = int(os.environ.get('LEASE_SETTLE_SECONDS', '85'))


def fail(messages: list[str]) -> None:
    print('\n'.join(f'- {message}' for message in messages), file=sys.stderr)
    raise SystemExit(1)


def request_json(path: str) -> Any:
    request = urllib.request.Request(
        f'{BASE_URL}{path}',
        headers={
            'Authorization': f'Bearer {ADMIN_TOKEN}',
            'Accept': 'application/json',
            'X-Request-Id': 'load-result-assertion',
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        body = error.read().decode('utf-8', errors='replace')
        raise RuntimeError(f'{error.code} {path}: {body}') from error


def metric(summary: dict[str, Any], name: str, value: str, default: float = 0.0) -> float:
    return float(summary.get('metrics', {}).get(name, {}).get('values', {}).get(value, default))


def payload_rows(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, dict):
        value = value.get('data', value)
    if isinstance(value, list):
        return [item for item in value if isinstance(item, dict)]
    if isinstance(value, dict):
        for key in ('items', 'records', 'content', 'rows', 'assignments'):
            items = value.get(key)
            if isinstance(items, list):
                return [item for item in items if isinstance(item, dict)]
    return []


def assert_assignments(assignments: list[dict[str, Any]], fixtures: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    active = [item for item in assignments if str(item.get('assignment_status', item.get('status', 'ACTIVE'))) == 'ACTIVE']
    student_ids = [int(item.get('student_id', item.get('studentId'))) for item in active if item.get('student_id', item.get('studentId')) is not None]
    bed_ids = [int(item.get('bed_id', item.get('bedId'))) for item in active if item.get('bed_id', item.get('bedId')) is not None]
    duplicate_students = [value for value, count in Counter(student_ids).items() if count > 1]
    duplicate_beds = [value for value, count in Counter(bed_ids).items() if count > 1]
    if duplicate_students:
        errors.append(f'duplicate active student assignments: {duplicate_students[:10]}')
    if duplicate_beds:
        errors.append(f'duplicate active bed assignments: {duplicate_beds[:10]}')

    team_rooms: dict[int, set[int]] = defaultdict(set)
    team_counts: Counter[int] = Counter()
    for item in active:
        team_id = item.get('team_id', item.get('teamId'))
        room_id = item.get('room_id', item.get('roomId'))
        if team_id is None:
            continue
        team_counts[int(team_id)] += 1
        if room_id is not None:
            team_rooms[int(team_id)].add(int(room_id))
    expected_teams = {
        int(team['teamId']): int(team.get('memberCount', len(team.get('members', []))))
        for team in fixtures.get('teams', [])
        if team.get('teamId') is not None
    }
    for team_id, expected in expected_teams.items():
        actual = team_counts.get(team_id, 0)
        if actual not in (0, expected):
            errors.append(f'team {team_id} partially succeeded: {actual}/{expected}')
        if actual == expected and len(team_rooms.get(team_id, set())) > 1:
            errors.append(f'team {team_id} was split across rooms')

    for item in active:
        student_gender = item.get('student_gender', item.get('gender'))
        room_gender = item.get('room_gender', item.get('gender_restriction'))
        if student_gender and room_gender and student_gender != room_gender:
            errors.append(f'hard gender constraint violated for assignment {item}')
            break
        if item.get('bed_operational_status') not in (None, 'ENABLED'):
            errors.append(f'disabled bed assigned: {item}')
            break
    return errors


def main() -> None:
    errors: list[str] = []
    if not ADMIN_TOKEN:
        errors.append('ADMIN_TOKEN is required')
    if not SUMMARY.exists():
        errors.append(f'k6 summary does not exist: {SUMMARY}')
    if not FIXTURES.exists():
        errors.append(f'fixtures do not exist: {FIXTURES}')
    if errors:
        fail(errors)

    summary = json.loads(SUMMARY.read_text(encoding='utf-8'))
    fixtures = json.loads(FIXTURES.read_text(encoding='utf-8'))
    p95_query = metric(summary, 'selection_main_query_duration', 'p(95)')
    p95_confirm = metric(summary, 'selection_confirm_duration', 'p(95)')
    error_rate = metric(summary, 'http_req_failed', 'rate')
    duplicate_accepted = metric(summary, 'duplicate_confirmation_accepted', 'count')
    valid_confirmations = metric(summary, 'selection_valid_confirmations', 'count')

    if p95_query >= 800:
        errors.append(f'main query p95 is {p95_query:.2f}ms, expected <800ms')
    if p95_confirm >= 1500:
        errors.append(f'confirm p95 is {p95_confirm:.2f}ms, expected <1500ms')
    if error_rate >= 0.01:
        errors.append(f'HTTP error rate is {error_rate:.4%}, expected <1%')
    if duplicate_accepted != 0:
        errors.append(f'{duplicate_accepted:g} duplicate confirmations were accepted')
    if valid_confirmations > 1:
        errors.append(f'{valid_confirmations:g} users successfully confirmed the same target bed')

    batch_id = int(fixtures['target']['batchId'])
    assignments = payload_rows(request_json(f'/api/v1/admin/batches/{batch_id}/assignments'))
    errors.extend(assert_assignments(assignments, fixtures))

    if LEASE_SETTLE_SECONDS > 0:
        print(f'waiting {LEASE_SETTLE_SECONDS}s for timeout leases to expire...')
        time.sleep(LEASE_SETTLE_SECONDS)
    concurrency = request_json('/api/v1/admin/settings/selection-concurrency').get('data', {})
    if concurrency.get('enabled') and int(concurrency.get('activeUsers', 0)) != 0:
        errors.append(f"timeout lease release failed: {concurrency.get('activeUsers')} active users remain")

    if errors:
        fail(errors)
    print(json.dumps({
        'p95MainQueryMs': p95_query,
        'p95ConfirmMs': p95_confirm,
        'httpErrorRate': error_rate,
        'duplicateActiveAssignments': 0,
        'teamPartialSuccess': 0,
        'hardConstraintErrors': 0,
        'timeoutLeaseReleaseRate': '100%',
    }, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
