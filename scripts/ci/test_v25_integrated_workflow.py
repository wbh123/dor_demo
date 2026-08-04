#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    file = ROOT / path
    assert file.exists(), f"missing required file: {path}"
    return file.read_text(encoding="utf-8")


def require(path: str, *tokens: str) -> None:
    text = read(path)
    for token in tokens:
        assert token in text, f"{path} missing token: {token}"


def forbid(path: str, *tokens: str) -> None:
    text = read(path)
    for token in tokens:
        assert token not in text, f"{path} contains forbidden token: {token}"


# Direct invitation automatically creates the internal forming team.
forbid("frontend/src/views/student/TeamView.vue", "createTeam()", "创建队伍", "先创建队伍")
require("backend-java/server/src/main/java/com/wust/dormitory/student/TeamService.java", "ensureFormingLeaderTeam", "inviteTeammate")
forbid("backend-java/model/src/main/resources/student/openapi-student.yaml", "operationId: createFormingTeam")

# Students without a batch can fill reusable preferences when the administrator enables it.
require("backend-java/server/src/main/java/com/wust/dormitory/selection/SelectionPolicyService.java", "ALLOW_DIRECT_PREFERENCE_WITHOUT_BATCH")
require("backend-java/server/src/main/java/com/wust/dormitory/student/StudentPreferenceService.java", "DIRECT_PREFERENCE_WITHOUT_BATCH_DISABLED")
require("frontend/src/views/student/QuestionnaireView.vue", "directPreferenceWithoutBatchAllowed")

# System administrator clears only the configured Redis database.
require("backend-java/model/src/main/resources/platform/openapi-platform.yaml", "/api/v1/platform/redis/clear", "operationId: clearPlatformRedis")
require("backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformRedisService.java", "flushDb", "CLEAR_REDIS")
forbid("backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformRedisService.java", "flushAll")
require("frontend/src/views/platform/PlatformDashboardView.vue", "清空Redis", "CLEAR_REDIS")

# Only the current strict task-based import workflow remains.
forbid("backend-java/model/src/main/resources/admin/openapi-import-policy.yaml", "/api/v1/admin/import/students:", "/api/v1/admin/import/rooms:")
require("backend-java/server/src/main/java/com/wust/dormitory/importworkflow/StrictImportHeaders.java", "STUDENT_HEADERS", "ROOM_HEADERS", "IMPORT_HEADER_MISMATCH")
forbid("backend-java/server/src/main/java/com/wust/dormitory/admin/StudentImportRowMapper.java", '"majorcode"', '"studentnumber"')

# Welcome editor uses fixed Chinese/English plus country-name selectors and one shared country editor.
require("frontend/src/components/admin/CountryWelcomeEditor.vue", "已配置国家或地区", "添加国家或地区")
require("frontend/src/views/admin/AdminDashboardView.vue", "CountryWelcomeEditor", "中国", "美国")
forbid("frontend/src/views/admin/AdminDashboardView.vue", "newLocale", "localeCode }}", "删除语言")

# Both room-change policy cards use the shared three-button segmented control.
require("frontend/src/components/admin/ThreeStateToggle.vue", "three-state-toggle")
require("frontend/src/views/admin/AdminRoomChangeView.vue", "ThreeStateToggle", "roomChangeMode", "roomExchangeMode")

# Brand logos are above decorative overlays; phone modal has a country calling-code selector.
require("frontend/src/layouts/AppShell.vue", "logo-safe-layer")
require("frontend/src/views/student/StudentHomeView.vue", "phone-modal-card", "countryCallingCode", "callingCodeOptions")

# Single beds are supported throughout layout and selection.
for path in (
    "backend-java/model/src/main/resources/admin/openapi-room-management.yaml",
    "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java",
    "frontend/src/components/admin/RoomLayoutEditor.vue",
):
    require(path, "SINGLE_BED")

# Actual-bed declarations are reviewed by administrators, including room-level approval and mobile UI.
require("backend-java/model/src/main/resources/bedconfirmation/openapi-bed-confirmation.yaml", "submitBedConfirmation", "listBedConfirmationRooms", "approveRoomBedConfirmations")
require("backend-java/server/src/main/java/com/wust/dormitory/bedconfirmation/BedConfirmationService.java", "PENDING", "approveRoom", "@Transactional")
require("frontend/src/views/student/AssignmentView.vue", "提交实际床位核查", "/api/v1/student/bed-confirmations")
require("frontend/src/views/admin/AdminBedConfirmationView.vue", "按寝室核查", "mobile-room-review", "sticky-room-actions")
require("frontend/src/router/index.ts", "admin-bed-confirmations")

print("V25 integrated workflow contracts passed")
