#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
FRONTEND = REPO_ROOT / "frontend/src"
JAVA_ROOT = REPO_ROOT / "backend-java/server/src/main/java/com/wust/dormitory"
MODEL_ROOT = REPO_ROOT / "backend-java/model/src/main/resources"
DOC_ROOT = REPO_ROOT / "backend-java/docs"


class AdminLayoutAllocationStudentResetTest(unittest.TestCase):
    def test_layout_editor_is_canvas_only_with_inline_controls(self) -> None:
        component = (FRONTEND / "components/admin/RoomLayoutEditor.vue").read_text(encoding="utf-8")
        styles = (FRONTEND / "admin-layout-canvas-refinement.css").read_text(encoding="utf-8")
        self.assertIn("layout-bed-type-actions", component)
        self.assertIn("layout-bed-rotate-button", component)
        self.assertIn("setUnitType(unit, 'LOFT_BED_DESK')", component)
        self.assertIn("setUnitType(unit, 'BUNK')", component)
        self.assertIn("cycleRotation(unit)", component)
        self.assertNotIn("layout-bed-type-grid", component)
        self.assertNotIn("layout-number-grid", component)
        self.assertNotIn("横向位置X", component)
        self.assertNotIn("纵向位置Z", component)
        self.assertNotIn("<select", component)
        self.assertIn(".room-layout-overlay", styles)
        self.assertIn("padding: 30px", styles)
        self.assertIn("border-radius: 26px", styles)
        self.assertIn("background: #ffffff", styles)

    def test_allocation_includes_all_eligible_students_and_reports_failures(self) -> None:
        service = (JAVA_ROOT / "allocation/AdminAllocationService.java").read_text(encoding="utf-8")
        batch = (FRONTEND / "views/admin/AdminBatchView.vue").read_text(encoding="utf-8")
        self.assertIn("allRemainingStudents", service)
        self.assertIn("allStudentsIncluded", service)
        self.assertIn("studentName", service)
        self.assertIn("failureReason", service)
        self.assertIn("unassigned", service)
        self.assertIn("未分配学生清单", batch)
        self.assertIn("studentName", batch)
        self.assertIn("failureReason", batch)
        self.assertNotIn("ACTIVE_TEAM_NOT_LOCKED", service)
        self.assertNotIn("account_status='ACTIVE'", service)
        self.assertNotIn("account_status = 'ACTIVE'", service)

    def test_admin_can_reset_password_and_complete_student_state(self) -> None:
        api = (MODEL_ROOT / "admin/openapi-student-account.yaml").read_text(encoding="utf-8")
        service = (JAVA_ROOT / "admin/StudentAccountAdminService.java").read_text(encoding="utf-8")
        controller = (JAVA_ROOT / "admin/StudentAccountAdminController.java").read_text(encoding="utf-8")
        view = (FRONTEND / "views/admin/AdminDataView.vue").read_text(encoding="utf-8")
        self.assertIn("/api/v1/admin/students/{studentId}/reset-password", api)
        self.assertIn("/api/v1/admin/students/{studentId}/reset-state", api)
        self.assertIn("password_hash=NULL", service)
        self.assertIn("account_status='PENDING'", service)
        self.assertIn("DELETE FROM bed_assignment", service)
        self.assertIn("DELETE FROM questionnaire_answer", service)
        self.assertIn("team_status='DISSOLVED'", service)
        self.assertIn("auditService.success", service)
        self.assertIn("implements StudentAccountAdminApi", controller)
        self.assertIn("重置密码", view)
        self.assertIn("完全重置", view)

    def test_reset_seed_script_and_data_dictionary_are_maintained(self) -> None:
        seed = (DOC_ROOT / "sql/reset_and_seed_test_data.sql").read_text(encoding="utf-8")
        dictionary = (DOC_ROOT / "database-dictionary.md").read_text(encoding="utf-8")
        self.assertIn("清空全部业务数据", seed)
        self.assertIn("管理员 username='admin'", seed)
        self.assertIn("allocation_run_result", seed)
        self.assertIn("student_notification", seed)
        self.assertIn("全部学生账号均为待激活", seed)
        self.assertIn("## 表清单", dictionary)
        self.assertIn("### `student`", dictionary)
        self.assertIn("### `bed_assignment`", dictionary)
        self.assertIn("### `allocation_run_result`", dictionary)
        self.assertIn("字段", dictionary)
        self.assertIn("含义", dictionary)
        self.assertIn("V9", dictionary)
        self.assertIn("每次新增或修改Flyway迁移", dictionary)


if __name__ == "__main__":
    unittest.main()
