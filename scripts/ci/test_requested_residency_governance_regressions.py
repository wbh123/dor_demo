#!/usr/bin/env python3
from pathlib import Path
import unittest

# 该契约覆盖本轮用户可见回归，并作为公开仓库完整构建前的聚焦门禁。
# 工作流恢复后由普通代码提交触发，避免验证逻辑与业务修改互相影响。
# 验证工作流会将精确失败位置或通过验证的业务代码提交回当前分支。
ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


class RequestedResidencyGovernanceRegressionTest(unittest.TestCase):
    def test_residency_adjustment_joins_layout_table(self):
        source = read("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminStudentResidencyAdjustmentService.java")
        self.assertIn("LEFT JOIN room_bed_layout layout ON layout.bed_id=bed.id", source)
        self.assertIn("layout.layout_x", source)
        self.assertNotIn("bed.layout_x", source)

    def test_student_assignment_prefers_latest_actual_bed(self):
        source = read("backend-java/server/src/main/java/com/wust/dormitory/student/StudentService.java")
        self.assertIn("LEFT JOIN room_assignment current_residency", source)
        self.assertIn("COALESCE(actual_bed.id, selected_bed.id) AS bed_id", source)

    def test_common_modal_replacements_are_present(self):
        admin_data = read("frontend/src/views/admin/AdminDataView.vue")
        self.assertIn("import AppModal", admin_data)
        self.assertGreaterEqual(admin_data.count("<AppModal"), 3)
        room_list = read("frontend/src/views/student/RoomListView.vue")
        self.assertIn("import AppModal", room_list)
        self.assertIn(':open="preferencePromptVisible"', room_list)
        room_change = read("frontend/src/views/student/StudentRoomChangeView.vue")
        self.assertIn("import AppModal", room_change)
        self.assertIn(':open="Boolean(target)"', room_change)
        residency = read("frontend/src/views/admin/AdminResidencyView.vue")
        self.assertIn("import AppModal", residency)
        self.assertIn(':open="Boolean(selected)"', residency)

    def test_report_builder_does_not_assign_const_reactive_binding(self):
        source = read("frontend/src/views/admin/AdminGovernanceView.vue")
        self.assertNotIn('v-model="reportDefinition"', source)
        self.assertIn(':model-value="reportDefinition"', source)
        self.assertIn("Object.assign(reportDefinition, value)", source)

    def test_selection_policy_preserves_direct_preference_setting(self):
        source = read("frontend/src/views/admin/AdminMatchingView.vue")
        self.assertIn("directPreferenceWithoutBatchAllowed", source)
        service = read("backend-java/server/src/main/java/com/wust/dormitory/selection/SelectionPolicyService.java")
        self.assertIn("int updated = jdbc.update", service)
        self.assertNotIn("setting_value=VALUES(setting_value)", service)

    def test_welcome_and_business_details_are_improved(self):
        shell = read("frontend/src/layouts/AppShell.vue")
        self.assertIn("welcome-modal-heading-row", shell)
        dashboard = read("frontend/src/views/admin/AdminDashboardView.vue")
        self.assertIn("auditActionText", dashboard)
        self.assertIn("auditTargetText", dashboard)
        matching = read("frontend/src/views/admin/AdminMatchingView.vue")
        self.assertIn("weight-manual compact-weight-manual", matching)

    def test_governance_has_audit_details_and_analytics_summary(self):
        source = read("frontend/src/views/admin/AdminGovernanceView.vue")
        self.assertIn("selectedAudit", source)
        self.assertIn("analytics-summary-grid", source)
        self.assertIn("queryAudit()", source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
