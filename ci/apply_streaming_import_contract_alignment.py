from pathlib import Path

ROOT = Path('private-repo')
PATH = ROOT / 'scripts/ci/test_import_recovery_anomaly_workflows.py'
text = PATH.read_text(encoding='utf-8')

old = '''import_worker = read(
    "backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportTaskWorker.java"
)
for token in (
    "SpreadsheetSupport.read",
    "PREVIEWING",
    "COMMITTING",
    "ROLLING_BACK",
    "enforceImportRowLimit",
    "validateRows",
    "executeCommit",
    "executeRollback",
    "LEASE_DURATION",
):
    require(import_worker, token, f"background import worker missing behavior: {token}")
'''
new = '''import_worker = read(
    "backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportTaskWorker.java"
)
streaming_reader = read(
    "backend-java/server/src/main/java/com/wust/dormitory/admin/StreamingSpreadsheetReader.java"
)
for token in (
    "StreamingSpreadsheetReader.read",
    "PREVIEWING",
    "COMMITTING",
    "ROLLING_BACK",
    "importRowLimit",
    "newValidationSession",
    "validation.validate",
    "executeCommitStreaming",
    "executeRollback",
    "LEASE_DURATION",
    "PREVIEW_WRITE_BATCH_SIZE = 500",
    "heartbeat",
):
    require(import_worker, token, f"background streaming import worker missing behavior: {token}")
forbid(import_worker, "SpreadsheetSupport.read", "background import worker must use the streaming spreadsheet reader")
for token in (
    "XSSFSheetXMLHandler",
    "readCsv",
    "totalRows >= maxRows",
    "SERVICE_QUOTA_EXCEEDED",
    "downstream.onRow",
):
    require(streaming_reader, token, f"streaming spreadsheet reader missing behavior: {token}")
'''
if new not in text:
    if old not in text:
        raise RuntimeError('import recovery contract: legacy worker assertion block missing')
    text = text.replace(old, new, 1)

PATH.write_text(text, encoding='utf-8')
print('import recovery contract aligned with streaming spreadsheet worker')
