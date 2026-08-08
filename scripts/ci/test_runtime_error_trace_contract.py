from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
filter_file = ROOT / 'backend-java/server/src/main/java/com/wust/dormitory/common/request/RequestIdFilter.java'
recorder_file = ROOT / 'backend-java/server/src/main/java/com/wust/dormitory/common/error/RuntimeErrorRecorder.java'
handler_file = ROOT / 'backend-java/server/src/main/java/com/wust/dormitory/common/error/GlobalExceptionHandler.java'
client_file = ROOT / 'frontend/src/api/client.ts'

assert filter_file.exists(), 'missing RequestIdFilter'
assert recorder_file.exists(), 'missing RuntimeErrorRecorder'

filter_source = filter_file.read_text(encoding='utf-8')
recorder_source = recorder_file.read_text(encoding='utf-8')
handler_source = handler_file.read_text(encoding='utf-8')
client_source = client_file.read_text(encoding='utf-8')

assert 'X-Request-Id' in filter_source
assert 'requestId' in filter_source
assert 'UUID.randomUUID' in filter_source
assert 'X-Request-Id' in client_source
assert 'crypto.randomUUID' in client_source
assert 'requestId' in handler_source
assert 'RuntimeErrorRecorder' in handler_source
assert 'debug/runtime-errors.ndjson' in recorder_source
for forbidden in ['Authorization', 'Cookie', 'password', 'Secret']:
    assert forbidden in recorder_source, f'missing explicit redaction guard for {forbidden}'
assert 'request.getHeader' not in recorder_source, 'recorder must not dump arbitrary request headers'
print('Runtime error trace contract passed')
