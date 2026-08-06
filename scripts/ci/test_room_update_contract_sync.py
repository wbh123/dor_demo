#!/usr/bin/env python3
# Prevent the effective RoomRequest, AdminController, service and edit form from drifting apart.
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INTERFACE_SPEC = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
ROOM_SPEC = ROOT / "backend-java/model/src/main/resources/admin/openapi-room-management.yaml"
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java"
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java"
FRONTEND = ROOT / "frontend/src/views/admin/AdminDormitoryView.vue"

for path in (INTERFACE_SPEC, ROOM_SPEC, CONTROLLER, SERVICE, FRONTEND):
    if not path.exists():
        raise AssertionError(f"缺少房间编辑契约文件：{path.relative_to(ROOT)}")

interface_spec = INTERFACE_SPEC.read_text(encoding="utf-8")
room_spec = ROOM_SPEC.read_text(encoding="utf-8")
controller = CONTROLLER.read_text(encoding="utf-8")
service = SERVICE.read_text(encoding="utf-8")
frontend = FRONTEND.read_text(encoding="utf-8")

path_ref = (
    "/api/v1/admin/rooms/{roomId}:\n"
    "    $ref: 'admin/openapi-room-management.yaml#/paths/~1api~1v1~1admin~1rooms~1{roomId}'"
)
if path_ref not in interface_spec:
    raise AssertionError("聚合 OpenAPI 未引用房间编辑真实契约文件")

required_line = (
    "required: [capacity, gender, educationLevelScope, residentScope, "
    "operationalStatus, reason]"
)
if required_line not in room_spec:
    raise AssertionError("有效 RoomRequest 必填字段未与房间编辑命令同步")

room_request_section = room_spec.split("    RoomRequest:", 1)[1].split(
    "    RoomBedLayoutRequest:", 1
)[0]
for token in (
        "educationLevelScope:",
        "enum: [UNDERGRADUATE_ONLY, GRADUATE_ONLY, MIXED]",
        "residentScope:",
        "enum: [DOMESTIC_ONLY, INTERNATIONAL_ONLY, MIXED]",
):
    if token not in room_request_section:
        raise AssertionError(f"有效 RoomRequest 缺少字段或枚举：{token}")
if "roomType:" in room_request_section:
    raise AssertionError("有效 RoomRequest 不应继续要求已由床位数量推导的 roomType")

for token in (
        "request.getEducationLevelScope().getValue()",
        "request.getResidentScope().getValue()",
        "public record RoomCommand(",
        "String educationLevelScope,",
        "String residentScope,",
):
    target = controller if token.startswith("request.") else service
    if token not in target:
        raise AssertionError(f"房间编辑后端契约不同步：{token}")

for token in (
        "type EducationScope = 'UNDERGRADUATE_ONLY' | 'GRADUATE_ONLY' | 'MIXED'",
        "educationLevelScope: EducationScope",
        "editForm.educationLevelScope = String(room.education_level_scope ?? 'MIXED') as EducationScope",
        "v-model=\"editForm.educationLevelScope\"",
):
    if token not in frontend:
        raise AssertionError(f"房间编辑前端契约不同步：{token}")

print("Effective room update OpenAPI/controller/service/frontend contract passed")
