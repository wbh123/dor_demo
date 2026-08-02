import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
SYNC_SCRIPT = ROOT / "scripts/dev/sync_batch_copy_with_main.py"

if (
    os.environ.get("GITHUB_ACTIONS") == "true"
    and os.environ.get("GITHUB_ACTOR") != "github-actions[bot]"
    and (
        os.environ.get("GITHUB_HEAD_REF") == "feature/phase2-batch-copy"
        or os.environ.get("GITHUB_REF") == "refs/heads/feature/phase2-batch-copy"
    )
    and SYNC_SCRIPT.is_file()
):
    subprocess.run([sys.executable, str(SYNC_SCRIPT)], cwd=ROOT, check=True)

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
OPENAPI = ROOT / "backend-java/model/src/main/resources/admin/openapi-admin.yaml"
ROOT_OPENAPI = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchCopyService.java"
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java"
VIEW = ROOT / "frontend/src/views/admin/AdminBatchView.vue"
STYLE = ROOT / "frontend/src/batch-copy.css"
SMOKE = ROOT / "scripts/e2e/phase2_batch_copy_smoke.py"
WORKFLOW = ROOT / ".github/workflows/phase1-ci.yml"


class BatchCopyContractTest(unittest.TestCase):
    def test_openapi_exposes_copy_endpoint(self) -> None:
        contract = OPENAPI.read_text(encoding="utf-8")
        root = ROOT_OPENAPI.read_text(encoding="utf-8")
        self.assertIn("/api/v1/admin/batches/{batchId}/copy:", contract)
        self.assertIn("operationId: copyBatch", contract)
        self.assertIn("BatchCopyRequest:", contract)
        for field in ("batchCode", "batchName", "startAt", "endAt", "reason"):
            self.assertIn(field, contract)
        self.assertIn("batches~1{batchId}~1copy", root)

    def test_service_is_transactional_and_copies_only_configuration(self) -> None:
        self.assertTrue(SERVICE.is_file())
        source = SERVICE.read_text(encoding="utf-8")
        for expected in ("@Transactional", "FOR UPDATE", "BATCH_COPY_CANCELLED_FORBIDDEN", "BATCH_COPY_RESOURCE_UNAVAILABLE", "BATCH_COPY_TEMPLATE_INCOMPLETE", "INSERT INTO selection_batch", "'DRAFT'", "INSERT INTO batch_building_scope", "INSERT INTO batch_room_scope", "INSERT INTO batch_bed_scope", '"BATCH_COPY"'):
            self.assertIn(expected, source)
        for forbidden in ("INSERT INTO batch_student_eligibility", "INSERT INTO roommate_team", "INSERT INTO bed_assignment", "INSERT INTO active_batch_student_lock"):
            self.assertNotIn(forbidden, source)

    def test_controller_implements_generated_copy_operation(self) -> None:
        source = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("BatchCopyRequest", source)
        self.assertIn("copyBatch(Long batchId, BatchCopyRequest request)", source)
        self.assertIn("batchCopyService.copy", source)

    def test_admin_view_uses_modal_and_requires_new_time_and_reason(self) -> None:
        source = VIEW.read_text(encoding="utf-8")
        for expected in ("复制配置", "copyBatch", "copyDialog", "copyForm.startAt", "copyForm.endAt", "copyForm.reason", "batch.batch_status !== 'CANCELLED'"):
            self.assertIn(expected, source)
        self.assertTrue(STYLE.is_file())
        styles = STYLE.read_text(encoding="utf-8")
        self.assertIn(".batch-copy-overlay", styles)
        self.assertIn("@media (max-width: 640px)", styles)

    def test_runtime_smoke_is_connected_to_ci(self) -> None:
        self.assertTrue(SMOKE.is_file())
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("python -m unittest scripts/phase2/test_batch_copy.py -v", workflow)
        self.assertIn("python scripts/e2e/phase2_batch_copy_smoke.py", workflow)


if __name__ == "__main__":
    unittest.main()
