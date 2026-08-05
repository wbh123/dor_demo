#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

BASE_URL = os.environ.get('BASE_URL', 'http://localhost:8080').rstrip('/')
ADMIN_TOKEN = os.environ.get('ADMIN_TOKEN', '').strip()
STUDENT_PASSWORD = os.environ.get('LOAD_STUDENT_PASSWORD', '').strip()
BATCH_ID = int(os.environ.get('TARGET_BATCH_ID', '0'))
ROOM_ID = int(os.environ.get('TARGET_ROOM_ID', '0'))
BED_ID = int(os.environ.get('TARGET_BED_ID', '0'))
STUDENT_COUNT = int(os.environ.get('LOAD_STUDENT_COUNT', '500'))
OUTPUT = Path(os.environ.get(
    'LOAD_FIXTURES',
    'scripts/load/generated/selection-fixtures.json',
))


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(2)


def request_json(path: str) -> Any:
    request = urllib.request.Request(
        f'{BASE_URL}{path}',
        headers={
            'Authorization': f'Bearer {ADMIN_TOKEN}',
            'Accept': 'application/json',
            'X-Request-Id': 'load-fixture-preparation',
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        body = error.read().decode('utf-8', errors='replace')
        fail(f'fixture API failed: {error.code} {path}\n{body}')


def rows(data: Any) -> list[dict[str, Any]]:
    payload = data.get('data', data) if isinstance(data, dict) else data
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if isinstance(payload, dict):
        for key in ('items', 'records', 'content', 'rows'):
            value = payload.get(key)
            if isinstance(value, list):
                return [item for item in value if isinstance(item, dict)]
    return []


def main() -> None:
    if not ADMIN_TOKEN:
        fail('ADMIN_TOKEN is required')
    if not STUDENT_PASSWORD:
        fail('LOAD_STUDENT_PASSWORD is required and is never written to the repository')
    if BATCH_ID <= 0 or ROOM_ID <= 0 or BED_ID <= 0:
        fail('TARGET_BATCH_ID, TARGET_ROOM_ID and TARGET_BED_ID must be positive integers')

    students: list[dict[str, Any]] = []
    page = 1
    while len(students) < STUDENT_COUNT:
        query = urllib.parse.urlencode({'page': page, 'size': min(200, STUDENT_COUNT)})
        current = rows(request_json(f'/api/v1/admin/students?{query}'))
        if not current:
            break
        for student in current:
            login_name = student.get('login_name') or student.get('student_number') or student.get('studentNumber')
            if not login_name:
                continue
            students.append({
                'studentId': student.get('id'),
                'loginName': str(login_name),
                'password': STUDENT_PASSWORD,
                'batchId': BATCH_ID,
            })
            if len(students) >= STUDENT_COUNT:
                break
        page += 1

    if len(students) < STUDENT_COUNT:
        fail(f'only {len(students)} usable students were found; {STUDENT_COUNT} required')

    teams = json.loads(os.environ.get('LOAD_TEAM_FIXTURES_JSON', '[]'))
    fixture = {
        'generatedBy': 'scripts/load/prepare-load-fixtures.py',
        'baseUrl': BASE_URL,
        'students': students,
        'teams': teams,
        'target': {'batchId': BATCH_ID, 'roomId': ROOM_ID, 'bedId': BED_ID},
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(fixture, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'wrote {len(students)} students and {len(teams)} teams to {OUTPUT}')


if __name__ == '__main__':
    main()
