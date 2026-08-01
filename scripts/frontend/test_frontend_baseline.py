#!/usr/bin/env python3
from __future__ import annotations

import json
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
FRONTEND = REPO_ROOT / "frontend"
SRC = FRONTEND / "src"


class FrontendBaselineTest(unittest.TestCase):
    def test_package_uses_openapi_generation_before_dev_and_build(self) -> None:
        package = json.loads((FRONTEND / "package.json").read_text(encoding="utf-8"))
        scripts = package["scripts"]
        self.assertIn("openapi-typescript", scripts["generate:api"])
        self.assertEqual(scripts["predev"], "npm run generate:api")
        self.assertIn("npm run generate:api", scripts["build"])
        for dependency in ("axios", "pinia", "vue-router"):
            self.assertIn(dependency, package["dependencies"])

    def test_api_layer_consumes_generated_schema(self) -> None:
        types = (SRC / "api/types.ts").read_text(encoding="utf-8")
        client = (SRC / "api/client.ts").read_text(encoding="utf-8")
        self.assertIn("import type { components } from './schema'", types)
        self.assertIn("components['schemas']", types)
        self.assertIn("CurrentUserSuccessResponse", types)
        self.assertIn("AssignmentAdjustmentRequest", types)
        self.assertIn("Authorization", client)
        self.assertIn("subscribeRoomEvents", client)
        self.assertIn("fetch(`/api/v1/realtime", client)

    def test_role_routes_cover_student_and_admin_flows(self) -> None:
        router = (SRC / "router/index.ts").read_text(encoding="utf-8")
        required_routes = (
            "/login",
            "student/batches/:batchId/questionnaire",
            "student/batches/:batchId/rooms",
            "student/teams",
            "student/batches/:batchId/assignment",
            "admin/data",
            "admin/dormitories",
            "admin/batches",
            "admin/assignments",
        )
        for route in required_routes:
            self.assertIn(route, router)
        self.assertIn("auth.restore()", router)
        self.assertIn("requiredRole", router)

    def test_phase1_views_exist(self) -> None:
        required = (
            "views/LoginView.vue",
            "layouts/AppShell.vue",
            "views/student/StudentHomeView.vue",
            "views/student/QuestionnaireView.vue",
            "views/student/RoomListView.vue",
            "views/student/RoomDetailView.vue",
            "views/student/TeamView.vue",
            "views/student/AssignmentView.vue",
            "views/admin/AdminDashboardView.vue",
            "views/admin/AdminDataView.vue",
            "views/admin/AdminDormitoryView.vue",
            "views/admin/AdminBatchView.vue",
            "views/admin/AdminAssignmentView.vue",
        )
        for relative in required:
            self.assertTrue((SRC / relative).is_file(), relative)

    def test_room_detail_supports_personal_team_and_realtime_modes(self) -> None:
        content = (SRC / "views/student/RoomDetailView.vue").read_text(encoding="utf-8")
        self.assertIn("subscribeRoomEvents", content)
        self.assertIn("/beds/${selectedBedIds.value[0]}/hold", content)
        self.assertIn("/teams/${teamId}/hold", content)
        self.assertIn("/teams/${teamId}/confirm", content)
        self.assertIn("/teams/${teamId}/release", content)
        self.assertIn("memberCount", content)

    def test_admin_pages_cover_core_management(self) -> None:
        data = (SRC / "views/admin/AdminDataView.vue").read_text(encoding="utf-8")
        dormitory = (SRC / "views/admin/AdminDormitoryView.vue").read_text(encoding="utf-8")
        batches = (SRC / "views/admin/AdminBatchView.vue").read_text(encoding="utf-8")
        assignments = (SRC / "views/admin/AdminAssignmentView.vue").read_text(encoding="utf-8")
        self.assertIn("/api/v1/admin/majors", data)
        self.assertIn("/api/v1/admin/students", data)
        self.assertIn("/api/v1/admin/rooms", dormitory)
        self.assertIn("allocation/preview", batches)
        self.assertIn("allocation/commit", batches)
        self.assertIn("assignments.csv", batches)
        self.assertIn("/assignments`,", assignments)
        self.assertIn("/adjust`,", assignments)
        self.assertIn("adjustment.reason", assignments)

    def test_template_demo_is_not_application_entry(self) -> None:
        app = (SRC / "App.vue").read_text(encoding="utf-8")
        main = (SRC / "main.ts").read_text(encoding="utf-8")
        self.assertNotIn("HelloWorld", app)
        self.assertIn("RouterView", app)
        self.assertIn(".use(pinia).use(router)", main)

    def test_vite_proxies_api_to_backend(self) -> None:
        config = (FRONTEND / "vite.config.ts").read_text(encoding="utf-8")
        self.assertIn("'/api'", config)
        self.assertIn("http://localhost:8080", config)


if __name__ == "__main__":
    unittest.main()
