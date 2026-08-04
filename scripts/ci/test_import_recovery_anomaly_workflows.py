from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"missing required file: {path}")
    text = target.read_text(encoding="utf-8")
    if target.suffix == ".vue":
        stem = target.with_suffix("")
        for suffix in (".logic.ts", ".template.html", ".css"):
            companion = Path(str(stem) + suffix)
            if companion.is_file():
                text += "
" + companion.read_text(encoding="utf-8")
    return text

def require(source: str, token: str, message: str) -> None:
    if token not in source:
        raise AssertionError(message)


def forbid(source: str, token: str, message: str) -> None:
    if token in source:
        raise AssertionError(message)


import_service = read(
    "backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportWorkflowService.java"
)
for token in (
    "PREVIEWED",
    "COMMITTED",
    "ROLLED_BACK",
    "SHA-256",
    "IDEMPOTENCY_CONFLICT",
    "SpreadsheetSupport.read",
    "applyTask",
    "rollbackJournal",
    "@Transactional",
):
    require(import_service, token, f"import workflow missing real behavior: {token}")

import_repository = read(
    "backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportTaskRepository.java"
)
for token in ("save", "findById", "findByIdempotencyKey", "list"):
    require(import_repository, token, f"import task repository missing method: {token}")

mutation_service = read(
    "backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportMutationService.java"
)
for token in (
    "studentAdminService.saveStudent",
    "roomImportService.applyRow",
    "rollbackJournal",
    "IMPORT_ROLLBACK_CONFLICT",
):
    require(mutation_service, token, f"import mutation workflow missing behavior: {token}")

import_controller = read(
    "backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportWorkflowController.java"
)
for token in (
    "implements ImportWorkflowApi",
    "previewImportTask",
    "listImportTasks",
    "getImportTask",
    "commitImportTask",
    "rollbackImportTask",
    "exportImportTaskErrors",
):
    require(import_controller, token, f"missing generated import workflow method: {token}")
for forbidden_route_annotation in (
    "@RequestMapping",
    "@GetMapping",
    "@PostMapping",
    "@RequestParam",
    "@RequestPart",
    "@RequestHeader",
    "@PathVariable",
):
    forbid(
        import_controller,
        forbidden_route_annotation,
        f"import workflow controller must not handwrite route annotation: {forbidden_route_annotation}",
    )

hold_inspector = read(
    "backend-java/server/src/main/java/com/wust/dormitory/operations/BedHoldKeyInspector.java"
)
for token in (
    "dormitory:batch:*:bed:*:hold",
    "batchId",
    "bedId",
    "ScanOptions",
    "orphanKeys",
):
    require(hold_inspector, token, f"bed hold inspector missing behavior: {token}")
for wrong_pattern in ("bed:hold:*", "student:hold:*", "team:hold:*", "dormitory:hold:*"):
    forbid(hold_inspector, wrong_pattern, f"bed hold inspector uses obsolete pattern: {wrong_pattern}")

recovery_service = read(
    "backend-java/server/src/main/java/com/wust/dormitory/operations/RedisRecoveryService.java"
)
for token in (
    "previewRecovery",
    "executeRecovery",
    "bedHoldKeyInspector.inspect",
    "recreatedKeys",
    "removedKeys",
    "dryRun",
):
    require(recovery_service, token, f"redis recovery missing behavior: {token}")

anomaly_service = read(
    "backend-java/server/src/main/java/com/wust/dormitory/operations/AnomalyWorkbenchService.java"
)
for token in (
    "UNKNOWN_BED_RESIDENCY",
    "DUPLICATE_ACTIVE_RESIDENCY",
    "STALE_BATCH_ROOM_LOCK",
    "STALE_BATCH_STUDENT_LOCK",
    "ORPHAN_BED_HOLD",
    "bedHoldKeyInspector.inspect",
    "resolutionHint",
):
    require(anomaly_service, token, f"anomaly workbench missing projection: {token}")

operations_controller = read(
    "backend-java/server/src/main/java/com/wust/dormitory/operations/OperationsController.java"
)
for token in (
    "previewRedisRecovery",
    "executeRedisRecovery",
    "listOperationsAnomalies",
    "getOperationsAnomalySummary",
):
    require(operations_controller, token, f"missing generated operations method: {token}")

root_openapi = read("backend-java/model/src/main/resources/openapi-interface.yaml")
for token in (
    "/api/v1/admin/import-tasks/preview:",
    "/api/v1/admin/import-tasks/{taskId}/commit:",
    "/api/v1/admin/operations/redis-recovery/preview:",
    "/api/v1/admin/operations/anomalies:",
):
    require(root_openapi, token, f"root OpenAPI missing path: {token}")

router = read("frontend/src/router/index.ts")
require(router, "admin/import-quality", "missing import quality page route")
require(router, "admin/anomalies", "missing anomaly workbench route")

print("import, recovery and anomaly workflow contracts are present")
