from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class DormitoryAdminStudentFlowContracts(unittest.TestCase):
    def read(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_common_sort_select_and_confirm_components(self) -> None:
        sortable = self.read("frontend/src/components/common/SortableTableHeader.vue")
        sorting = self.read("frontend/src/composables/useTableSort.ts")
        selector = self.read("frontend/src/components/common/RemoteEntitySelect.vue")
        confirm = self.read("frontend/src/components/common/ActionConfirmDialog.vue")
        self.assertIn("SortDirection", sortable)
        self.assertIn("'asc'", sortable)
        self.assertIn("'desc'", sortable)
        self.assertIn("useTableSort", sorting)
        self.assertIn("remote-search", selector)
        self.assertIn("AppModal", confirm)

    def test_dormitory_buildings_are_editable_table_rows(self) -> None:
        view = self.read("frontend/src/views/admin/AdminDormitoryView.vue")
        service = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/admin/BuildingManagementService.java"
        )
        mapper = self.read(
            "backend-java/server/src/main/resources/mapper/admin/BuildingManagementMapper.xml"
        )
        self.assertIn("全部楼栋", view)
        self.assertIn("openBuildingEditor", view)
        self.assertIn("building-status-bubble", view)
        self.assertIn("SortableTableHeader", view)
        self.assertIn("update(", service)
        self.assertIn("countIncompatibleRooms", mapper)

    def test_room_layout_exposes_occupancy_reasons(self) -> None:
        editor = self.read("frontend/src/components/admin/RoomLayoutEditor.vue")
        service = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/residency/BedOccupancyQueryService.java"
        )
        mapper = self.read(
            "backend-java/server/src/main/resources/mapper/residency/BedOccupancyMapper.xml"
        )
        guard = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutOccupancyGuardAspect.java"
        )
        self.assertIn("blockingReason", editor)
        self.assertIn("occupancySource", service)
        self.assertIn("bed_assignment", mapper)
        self.assertIn("room_assignment", mapper)
        self.assertIn("layoutMapper", guard)
        self.assertNotIn("RoomLayoutService roomLayoutService", guard)

    def test_students_scope_and_rooms_are_sortable(self) -> None:
        data = self.read("frontend/src/views/admin/AdminDataView.vue")
        scope = self.read(
            "frontend/src/features/admin-batch/components/BatchScopeDialog.vue"
        )
        dormitory = self.read("frontend/src/views/admin/AdminDormitoryView.vue")
        self.assertIn("SortableTableHeader", data)
        self.assertIn("studentSortField", scope)
        self.assertIn("roomSortField", scope)
        self.assertIn("SortableTableHeader", dormitory)

    def test_notification_center_uses_entity_selectors_and_private_message(self) -> None:
        panel = self.read(
            "frontend/src/features/admin-governance/components/NotificationCenterPanel.vue"
        )
        recipients = self.read("frontend/src/components/admin/RecipientSelector.vue")
        message = self.read("frontend/src/components/admin/StudentMessageDialog.vue")
        data_view = self.read("frontend/src/views/admin/AdminDataView.vue")
        options = self.read(
            "backend-java/server/src/main/resources/mapper/notification/NotificationOptionMapper.xml"
        )
        self.assertIn("notification-workbench", panel)
        self.assertIn("RemoteEntitySelect", recipients)
        self.assertNotIn("学生编号列表</span><textarea", recipients)
        self.assertIn("发送私信", data_view)
        self.assertIn("studentIds", message)
        self.assertIn("LIMIT 20", options)

    def test_team_selection_uses_confirmed_member_assignments(self) -> None:
        team = self.read("frontend/src/views/student/TeamView.vue")
        room = self.read("frontend/src/views/student/RoomDetailView.vue")
        panel = self.read("frontend/src/components/student/TeamBedAssignmentPanel.vue")
        service = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/student/TeamSelectionMemberService.java"
        )
        team_service = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/student/TeamService.java"
        )
        personal = self.read("frontend/src/views/student/RoomListView.vue")
        self.assertIn("ActionConfirmDialog", team)
        self.assertIn("team-card-full-width", team)
        self.assertIn("所有床位由队长统一确定", room)
        self.assertIn("memberAssignments", panel)
        self.assertIn("confirmedMembers", service)
        self.assertIn("INVITATION_CANCELLED", team_service)
        self.assertIn("ActionConfirmDialog", personal)

    def test_room_exchange_is_exact_and_privacy_preserving(self) -> None:
        view = self.read("frontend/src/views/student/StudentRoomChangeView.vue")
        service = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/roomexchange/RoomExchangeCandidateService.java"
        )
        mapper = self.read(
            "backend-java/server/src/main/resources/mapper/roomexchange/RoomExchangeCandidateMapper.xml"
        )
        self.assertIn("寝室交换", view)
        self.assertIn("exchange-strategy-card", view)
        self.assertIn("studentNumber", service)
        self.assertIn("studentName", service)
        self.assertIn("buildingId", service)
        self.assertIn("roomNumber", service)
        self.assertIn("target.student_number=#{studentNumber}", mapper)
        self.assertNotIn("LIKE CONCAT", mapper)

    def test_admin_bed_adjustment_uses_shared_selector_and_swap(self) -> None:
        selector = self.read("frontend/src/components/admin/DormitoryBedSelector.vue")
        residency = self.read("frontend/src/views/admin/AdminResidencyView.vue")
        assignment = self.read("frontend/src/views/admin/AdminAssignmentView.vue")
        data = self.read("frontend/src/views/admin/AdminDataView.vue")
        swap = self.read(
            "backend-java/server/src/main/java/com/wust/dormitory/residency/AdminBedSwapService.java"
        )
        swap_mapper = self.read(
            "backend-java/server/src/main/resources/mapper/residency/AdminBedSwapMapper.xml"
        )
        context_mapper = self.read(
            "backend-java/server/src/main/resources/mapper/admin/AdminResidencyAdjustmentMapper.xml"
        )
        self.assertIn("occupancySource", selector)
        self.assertIn("DormitoryBedSelector", residency)
        self.assertIn("DormitoryBedSelector", assignment)
        self.assertIn("DormitoryBedSelector", data)
        self.assertIn("swapBeds", swap)
        self.assertIn("FOR UPDATE", swap_mapper)
        self.assertIn("swap_required", context_mapper)

    def test_brand_welcome_and_permission_layouts(self) -> None:
        brand = self.read("frontend/src/brand-layout-refinement.css")
        permissions = self.read("frontend/src/platform-feature-compact.css")
        main = self.read("frontend/src/main.ts")
        self.assertIn("--brand-logo-size", brand)
        self.assertIn("welcome-modal-message", brand)
        self.assertIn("grid-template-columns:repeat(3", permissions)
        self.assertIn("platform-feature-compact.css", main)


if __name__ == "__main__":
    unittest.main()
