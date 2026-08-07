from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class AdminRuntimeRegressionContracts(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_student_list_adjustment_uses_formal_route_and_typed_null(self) -> None:
        controller = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/admin/"
            "AdminStudentResidencyAdjustmentController.java"
        )
        service = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/admin/"
            "AdminStudentResidencyAdjustmentService.java"
        )
        mapper = self.read(
            "backend-java/server/src/main/resources/mapper/admin/"
            "AdminResidencyAdjustmentMapper.xml"
        )
        self.assertIn('RequestMapping("/api/v1/admin/students/{studentId}")', controller)
        self.assertIn('GetMapping("/residency-adjustment-context")', controller)
        self.assertIn('PostMapping("/residency-adjustment")', controller)
        self.assertIn("adjustmentMapper.findCompatibleBeds", service)
        self.assertIn("#{currentBedId,jdbcType=BIGINT}", mapper)

    def test_admin_bed_change_marks_manual_adjustment(self) -> None:
        aspect = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/residency/"
            "AdminResidencyBedAdjustmentAspect.java"
        )
        mapper = self.read(
            "backend-java/server/src/main/resources/mapper/residency/"
            "ResidencyAdminSourceMapper.xml"
        )
        self.assertIn("operator.isAdmin()", aspect)
        self.assertIn("sourceMapper.markManualAdjustment", aspect)
        self.assertIn("assignment_method='MANUAL_ADJUSTMENT'", mapper)
        view = self.read("frontend/src/views/admin/AdminResidencyView.vue")
        compact_view = view.replace(" ", "")
        self.assertIn("MANUAL_ADJUSTMENT:'管理员修改'", compact_view)
        self.assertIn("TransientNotice", view)
        self.assertIn("退宿办理成功", view)

    def test_rule_revision_validation_is_human_readable(self) -> None:
        client = self.read("frontend/src/api/client.ts")
        rules = self.read("frontend/src/views/admin/AdminRuleTemplateView.vue")
        self.assertIn("修改原因至少填写2个字符", client)
        self.assertIn("form.changeReason.trim().length < 2", rules)
        self.assertIn('minlength="2"', rules)

    def test_heavy_allocation_exposes_busy_state(self) -> None:
        page = self.read("frontend/src/views/admin/AdminBatchView.vue")
        template = self.read("frontend/src/views/admin/AdminBatchView.template.html")
        dialog = self.read(
            "frontend/src/features/admin-batch/components/BatchAllocationPreviewDialog.vue"
        )
        self.assertIn("const allocationCommitting = ref(false)", page)
        self.assertIn("finally", page)
        self.assertIn(':busy="allocationCommitting"', template)
        self.assertIn("busy: boolean", dialog)
        self.assertIn("正在执行统一分配", dialog)
        self.assertIn(":busy=\"busy\"", dialog)
        self.assertIn("请勿重复操作", dialog)

    def test_assignment_adjustment_is_visible_modal(self) -> None:
        view = self.read("frontend/src/views/admin/AdminAssignmentView.vue")
        self.assertIn("import AppModal", view)
        self.assertIn('<AppModal :open="Boolean(selectedAssignment)"', view)
        self.assertIn("adjusting", view)
        self.assertNotIn('class="panel drawer-panel"', view)

    def test_operations_include_committed_allocations(self) -> None:
        service = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/operations/OperationsService.java"
        )
        mapper = self.read(
            "backend-java/server/src/main/resources/mapper/operations/OperationsMetricsMapper.xml"
        )
        self.assertIn("OperationsMetricsMapper metricsMapper", service)
        self.assertIn("metricsMapper.countOccupiedBeds()", service)
        self.assertIn("FROM bed_assignment", mapper)
        self.assertIn("COUNT(DISTINCT occupied.bed_id)", mapper)
        self.assertIn("COUNT(DISTINCT occupied.student_id)", mapper)
        self.assertIn("assignment.assignment_status='ACTIVE'", mapper)

    def test_governance_download_uses_authenticated_client(self) -> None:
        panel = self.read("frontend/src/components/admin/ExportTaskPanel.vue")
        self.assertIn("import { api }", panel)
        self.assertIn("responseType: 'blob'", panel)
        self.assertNotIn("anchor.href=`/api/v1/admin/governance/exports", panel)
        self.assertIn("下载中…", panel)

    def test_notification_template_read_accepts_related_entitlements(self) -> None:
        service = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/notification/"
            "NotificationTemplateService.java"
        )
        self.assertIn("requireTemplateReadAccess", service)
        self.assertIn("P3_NOTIFICATION_TEMPLATE_MANAGE", service)
        self.assertIn("P3_NOTIFICATION_SEND", service)

    def test_modal_content_does_not_double_pad(self) -> None:
        residency = self.read("frontend/src/views/admin/AdminResidencyView.vue")
        self.assertIn(".bed-confirm-dialog{", residency)
        self.assertIn("width:100%", residency)
        assignment = self.read("frontend/src/views/admin/AdminAssignmentView.vue")
        self.assertIn(".adjustment-dialog{display:grid", assignment)


if __name__ == "__main__":
    unittest.main()
