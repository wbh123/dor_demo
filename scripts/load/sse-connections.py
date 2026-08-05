#!/usr/bin/env python3
from __future__ import annotations

import asyncio
import json
import os
import ssl
import sys
import urllib.parse
from pathlib import Path
from typing import Any

BASE_URL = os.environ.get('BASE_URL', 'http://localhost:8080').rstrip('/')
FIXTURES = Path(os.environ.get('LOAD_FIXTURES', 'scripts/load/generated/selection-fixtures.json'))
CONNECTIONS = int(os.environ.get('SSE_CONNECTIONS', '500'))
HOLD_SECONDS = int(os.environ.get('SSE_HOLD_SECONDS', '45'))
CONNECT_TIMEOUT = float(os.environ.get('SSE_CONNECT_TIMEOUT', '20'))
RESULT = Path(os.environ.get('SSE_RESULT', 'scripts/load/generated/sse-result.json'))


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(2)


async def login(student: dict[str, Any]) -> str:
    parsed = urllib.parse.urlsplit(BASE_URL)
    use_tls = parsed.scheme == 'https'
    port = parsed.port or (443 if use_tls else 80)
    ssl_context = ssl.create_default_context() if use_tls else None
    reader, writer = await asyncio.wait_for(
        asyncio.open_connection(parsed.hostname, port, ssl=ssl_context),
        timeout=CONNECT_TIMEOUT,
    )
    body = json.dumps({
        'loginName': student['loginName'],
        'password': student['password'],
    }, ensure_ascii=False).encode('utf-8')
    path = f"{parsed.path.rstrip('/')}/api/v1/auth/login" or '/api/v1/auth/login'
    request = (
        f'POST {path} HTTP/1.1\r\n'
        f'Host: {parsed.netloc}\r\n'
        'Content-Type: application/json\r\n'
        f'Content-Length: {len(body)}\r\n'
        'Connection: close\r\n\r\n'
    ).encode('ascii') + body
    writer.write(request)
    await writer.drain()
    raw = await asyncio.wait_for(reader.read(), timeout=CONNECT_TIMEOUT)
    writer.close()
    await writer.wait_closed()
    header, _, response_body = raw.partition(b'\r\n\r\n')
    if b' 200 ' not in header.split(b'\r\n', 1)[0]:
        raise RuntimeError(f'login failed: {header[:120]!r}')
    payload = json.loads(response_body.decode('utf-8'))
    token = payload.get('data', {}).get('token')
    if not token:
        raise RuntimeError('login response has no token')
    return str(token)


async def open_sse(student: dict[str, Any], room_id: int) -> tuple[bool, str]:
    token = await login(student)
    parsed = urllib.parse.urlsplit(BASE_URL)
    use_tls = parsed.scheme == 'https'
    port = parsed.port or (443 if use_tls else 80)
    ssl_context = ssl.create_default_context() if use_tls else None
    reader, writer = await asyncio.wait_for(
        asyncio.open_connection(parsed.hostname, port, ssl=ssl_context),
        timeout=CONNECT_TIMEOUT,
    )
    path = (
        f"{parsed.path.rstrip('/')}/api/v1/realtime/batches/"
        f"{student['batchId']}/rooms/{room_id}"
    )
    request = (
        f'GET {path} HTTP/1.1\r\n'
        f'Host: {parsed.netloc}\r\n'
        f'Authorization: Bearer {token}\r\n'
        'Accept: text/event-stream\r\n'
        'Cache-Control: no-cache\r\n'
        'Connection: keep-alive\r\n'
        f'X-Request-Id: sse-load-{student.get("studentId", student["loginName"])}\r\n\r\n'
    ).encode('ascii')
    writer.write(request)
    await writer.drain()
    status_line = await asyncio.wait_for(reader.readline(), timeout=CONNECT_TIMEOUT)
    headers: list[bytes] = []
    while True:
        line = await asyncio.wait_for(reader.readline(), timeout=CONNECT_TIMEOUT)
        if line in (b'\r\n', b'\n', b''):
            break
        headers.append(line.lower())
    accepted = b' 200 ' in status_line and any(b'text/event-stream' in line for line in headers)
    if not accepted:
        writer.close()
        await writer.wait_closed()
        return False, status_line.decode('latin-1', errors='replace').strip()
    try:
        end = asyncio.get_running_loop().time() + HOLD_SECONDS
        while asyncio.get_running_loop().time() < end:
            try:
                chunk = await asyncio.wait_for(reader.read(1), timeout=5)
                if chunk == b'':
                    return False, 'connection closed before hold interval ended'
            except asyncio.TimeoutError:
                continue
    finally:
        writer.close()
        await writer.wait_closed()
    return True, 'connected'


async def main_async() -> dict[str, Any]:
    if not FIXTURES.exists():
        fail(f'fixture file not found: {FIXTURES}')
    fixture = json.loads(FIXTURES.read_text(encoding='utf-8'))
    students = fixture.get('students', [])
    room_id = int(fixture.get('target', {}).get('roomId', 0))
    if room_id <= 0:
        fail('target.roomId is required')
    if len(students) < CONNECTIONS:
        fail(f'{CONNECTIONS} students required, only {len(students)} found')

    results = await asyncio.gather(
        *(open_sse(student, room_id) for student in students[:CONNECTIONS]),
        return_exceptions=True,
    )
    accepted = sum(1 for item in results if item == (True, 'connected'))
    errors = [str(item) for item in results if item != (True, 'connected')]
    return {
        'requestedConnections': CONNECTIONS,
        'acceptedConnections': accepted,
        'holdSeconds': HOLD_SECONDS,
        'successRate': accepted / CONNECTIONS if CONNECTIONS else 0,
        'errors': errors[:20],
    }


def main() -> None:
    result = asyncio.run(main_async())
    RESULT.parent.mkdir(parents=True, exist_ok=True)
    RESULT.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if result['acceptedConnections'] != result['requestedConnections']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
