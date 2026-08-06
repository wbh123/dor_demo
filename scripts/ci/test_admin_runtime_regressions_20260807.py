from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class AdminRuntimeRegressionContracts(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_student_residency_adjustment_uses_real_contract(self) -> None:
        source = self.read("frontend/src/views/admin/AdminDataView.logic.ts")
        self.assertIn("/direct-assignment", source)
        self.assertIn("api.post('/api/v1/admin/residencies'", source)
        self.assertIn("studentId: Number(target.id)", source)
        self.assertNotIn("residency-adjustment-context", source)
        self.assertNotIn("/residency-adjustment`", source)

    def test_admin_bed_change_marks_manual_adjustment(self) -> None:
        service = self.read("backend-java/server/src/main/java/com/wust/dormitory/residency/ResidencyService.java")
        self.assertIn("assignment_method=CASE WHEN :adminAdjustment=1", service)
        self.assertIn("operator.isAdmin() ? 1 : 0", service)
        view = self.read("frontend/src/views/admin/AdminResidencyView.vue")
        self.assertIn("MANUAL_ADJUSTMENT: '管理员修改'", view)
        self.assertIn("TransientNotice", view)

    def test_rule_revision_reasons_are_validated_before_submit(self) -> None:
        matching = self.read("frontend/src/views/admin/AdminMatchingView.vue")
        rules = self.read("frontend/src/views/admin/AdminRuleTemplateView.vue")
        self.assertIn("form.reason.trim().length < 2", matching)
        self.assertIn('minlength="2"', matching)
        self.assertIn("form.changeReason.trim().length < 2", rules)
        self.assertIn('minlength="2"', rules)

    def test_heavy_allocation_exposes_busy_state(self) -> None:
        logic = self.read("frontend/src/views/admin/AdminBatchView.logic.ts")
        dialog = self.read("frontend/src/features/admin-batch/components/BatchAllocationPreviewDialog.vue")
        self.assertIn("allocationPreviewLoading", logic)
        self.assertIn("allocationCommitting", logic)
        self.assertIn("正在执行统一分配", dialog)
        self.assertIn(":busy=", dialog)

    def test_assignment_adjustment_is_visible_modal(self) -> None:
        view = self.read("frontend/src/views/admin/AdminAssignmentView.vue")
        self.assertIn("import AppModal", view)
        self.assertIn('<AppModal :open="Boolean(selectedAssignment)"', view)
        self.assertIn("adjusting", view)

    def test_allocation_commit_synchronizes_residency_facts(self) -> None:
        service = self.read("backend-java/server/src/main/java/com/wust/dormitory/allocation/AdminAllocationService.java")
        self.assertIn("ResidencyService residencyService", service)
        self.assertIn("residencyService.synchronizeBedAssignment", service)

    def test_governance_download_uses_authenticated_client(self) -> None:
        panel = self.read("frontend/src/components/admin/ExportTaskPanel.vue")
        self.assertIn("import { api }", panel)
        self.assertIn("responseType: 'blob'", panel)
        self.assertNotIn("anchor.href=`/api/v1/admin/governance/exports", panel)

    def test_notification_template_read_accepts_related_entitlements(self) -> None:
        service = self.read("backend-java/server/src/main/java/com/wust/dormitory/notification/NotificationTemplateService.java")
        self.assertIn("requireTemplateReadAccess", service)
        self.assertIn("P3_NOTIFICATION_TEMPLATE_MANAGE", service)
        self.assertIn("P3_NOTIFICATION_SEND", service)

    def test_modal_contents_do_not_double_pad(self) -> None:
        css = self.read("frontend/src/views/admin/AdminDataView.css")
        self.assertNotIn(".student-dialog{width:min(760px,calc(100vw - 32px));padding:24px}", css)
        residency = self.read("frontend/src/views/admin/AdminResidencyView.vue")
        self.assertNotIn(".bed-confirm-dialog{width:min(760px,calc(100vw - 32px));padding:24px}", residency)


if __name__ == "__main__":
    unittest.main()
