#!/usr/bin/env python3
from __future__ import annotations

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = REPO_ROOT / "backend-java/server/src/main/java/com/wust/dormitory"


class Phase1BackendSourceTest(unittest.TestCase):
    def test_repository_contains_fixed_parent_bom(self) -> None:
        root_pom = REPO_ROOT / "backend-java/pom.xml"
        fixed_bom = REPO_ROOT / "backend-java/build-support/general-bom3/pom.xml"
        ET.parse(root_pom)
        ET.parse(fixed_bom)
        content = root_pom.read_text(encoding="utf-8")
        self.assertIn("<relativePath>build-support/general-bom3/pom.xml</relativePath>", content)

    def test_boot4_uses_flyway_starter_for_auto_migration(self) -> None:
        server_pom = (REPO_ROOT / "backend-java/server/pom.xml").read_text(encoding="utf-8")
        self.assertIn("spring-boot-starter-flyway", server_pom)

    def test_security_is_stateless_and_role_scoped(self) -> None:
        content = (JAVA_ROOT / "security/SecurityConfig.java").read_text(encoding="utf-8")
        self.assertIn("SessionCreationPolicy.STATELESS", content)
        self.assertIn('requestMatchers("/api/v1/admin/**").hasRole("ADMIN")', content)
        self.assertIn('requestMatchers("/api/v1/student/**", "/api/v1/realtime/**").hasRole("STUDENT")', content)
        self.assertIn("ResponseFactory.error", content)

    def test_jackson2_mapper_is_explicitly_configured_for_boot4_tokens(self) -> None:
        config = JAVA_ROOT / "common/config/Jackson2Config.java"
        self.assertTrue(config.is_file(), config.as_posix())
        content = config.read_text(encoding="utf-8")
        self.assertIn("com.fasterxml.jackson.databind.ObjectMapper", content)
        self.assertIn("@Bean", content)
        self.assertIn("findAndAddModules", content)

    def test_redis_hold_is_atomic_and_token_guarded(self) -> None:
        content = (JAVA_ROOT / "selection/BedHoldService.java").read_text(encoding="utf-8")
        self.assertIn("setIfAbsent", content)
        self.assertIn("psetex", content)
        self.assertIn("value ~= ARGV[1]", content)
        self.assertNotIn("redisTemplate.hasKey", content)

    def test_final_assignment_is_transactional(self) -> None:
        student_service = (JAVA_ROOT / "student/StudentService.java").read_text(encoding="utf-8")
        allocation_service = (JAVA_ROOT / "allocation/AdminAllocationService.java").read_text(encoding="utf-8")
        self.assertRegex(student_service, r"@Transactional\s+public Map<String, Object> confirm\(")
        self.assertRegex(student_service, r"@Transactional\s+public void confirmTeam\(")
        self.assertRegex(allocation_service, r"@Transactional\s+public Map<String, Object> commit\(")
        self.assertIn("INSERT INTO bed_assignment", student_service)
        self.assertIn("INSERT INTO assignment_history", student_service)
        self.assertIn("afterCommit", student_service)
        self.assertIn("INSERT INTO allocation_run", allocation_service)

    def test_admin_allocation_preserves_locked_teams_and_includes_all_students(self) -> None:
        content = (JAVA_ROOT / "allocation/AdminAllocationService.java").read_text(encoding="utf-8")
        self.assertIn("team-first-all-students-v2", content)
        self.assertIn("lockedTeams", content)
        self.assertIn("allRemainingStudents", content)
        self.assertIn("allStudentsIncluded", content)
        self.assertIn("没有同一房间可容纳完整锁定队伍", content)
        self.assertIn("team_status='COMPLETED'", content)
        self.assertIn("failureReason", content)
        self.assertNotIn("ACTIVE_TEAM_NOT_LOCKED", content)
        self.assertNotIn("account_status='ACTIVE'", content)

    def test_assignment_query_and_adjustment_are_audited(self) -> None:
        query = (JAVA_ROOT / "allocation/AssignmentQueryService.java").read_text(encoding="utf-8")
        adjustment = (JAVA_ROOT / "allocation/AssignmentAdjustmentService.java").read_text(encoding="utf-8")
        controller = (JAVA_ROOT / "admin/AdminController.java").read_text(encoding="utf-8")
        self.assertIn("availableBeds", query)
        self.assertIn("batch_bed_scope", query)
        self.assertRegex(adjustment, r"@Transactional\s+public Map<String, Object> adjust\(")
        self.assertIn("MANUAL_ADJUSTMENT", adjustment)
        self.assertIn("INSERT INTO assignment_history", adjustment)
        self.assertIn("auditService.success", adjustment)
        self.assertIn("previousData", adjustment)
        self.assertIn("currentData", adjustment)
        self.assertIn("assignmentQueryService.list", controller)
        self.assertIn("adjustmentService.adjust", controller)

    def test_assignment_export_does_not_probe_resource_length(self) -> None:
        controller = (JAVA_ROOT / "admin/AdminController.java").read_text(encoding="utf-8")
        self.assertNotIn("resource.contentLength()", controller)
        self.assertIn("ResponseEntity<Resource> exportAssignments", controller)

    def test_batch_bed_scope_is_enforced(self) -> None:
        guard = (JAVA_ROOT / "selection/BedScopeGuard.java").read_text(encoding="utf-8")
        controller = (JAVA_ROOT / "student/StudentController.java").read_text(encoding="utf-8")
        self.assertIn("batch_bed_scope", guard)
        self.assertIn("BED_OUT_OF_SCOPE", guard)
        self.assertIn("bedScopeGuard.requireAllowed", controller)
        self.assertIn("bedScopeGuard.filterRoomSnapshot", controller)

    def test_matching_is_deterministic_and_has_conflict_penalty(self) -> None:
        content = (JAVA_ROOT / "matching/MatchingService.java").read_text(encoding="utf-8")
        self.assertIn("schemeService.policyForBatch", content)
        self.assertIn("smokingAcceptance", content)
        self.assertIn('rule(rules, "smokingConflictPenalty", 25)', content)
        self.assertIn("Math.max(0, Math.min(100", content)
        self.assertNotIn("Random", content)

    def test_sse_is_room_scoped_and_has_heartbeat(self) -> None:
        content = (JAVA_ROOT / "realtime/RoomEventHub.java").read_text(encoding="utf-8")
        self.assertIn("channel(batchId, roomId)", content)
        self.assertIn('@Scheduled(fixedDelay = 20_000)', content)
        self.assertIn('"HEARTBEAT"', content)
        self.assertIn("emitter.onCompletion", content)
        self.assertIn("emitter.onTimeout", content)

    def test_student_service_never_reads_removed_student_fields(self) -> None:
        sources = "\n".join(path.read_text(encoding="utf-8") for path in JAVA_ROOT.rglob("*.java"))
        for removed in ("class_name", "grade_year", "housing_eligibility", "profile_status", "student.data_source"):
            self.assertNotIn(removed, sources)
        self.assertIn("m.major_code", sources)
        self.assertIn("s.major_id", sources)

    def test_controllers_do_not_own_business_transactions(self) -> None:
        controller_paths = list(JAVA_ROOT.rglob("*Controller.java"))
        self.assertGreaterEqual(len(controller_paths), 4)
        for path in controller_paths:
            content = path.read_text(encoding="utf-8")
            self.assertNotIn("@Transactional", content, path.as_posix())
            self.assertNotIn("NamedParameterJdbcTemplate", content, path.as_posix())

    def test_no_example_java_sources_remain(self) -> None:
        names = [path.name for path in JAVA_ROOT.rglob("*.java")]
        self.assertFalse(any(name.startswith("Example") for name in names))


if __name__ == "__main__":
    unittest.main()
