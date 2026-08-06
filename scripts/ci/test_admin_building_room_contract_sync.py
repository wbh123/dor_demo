#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ROOT_SPEC = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
ADMIN_SPEC = ROOT / "backend-java/model/src/main/resources/admin/openapi-admin.yaml"
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java"
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java"

for path in (ROOT_SPEC, ADMIN_SPEC, CONTROLLER, SERVICE):
    if not path.exists():
        raise AssertionError(f"缺少管理端楼栋/房间契约文件：{path.relative_to(ROOT)}")

root_spec = ROOT_SPEC.read_text(encoding="utf-8")
admin_spec = ADMIN_SPEC.read_text(encoding="utf-8")
controller = CONTROLLER.read_text(encoding="utf-8")
service = SERVICE.read_text(encoding="utf-8")

for token in (
        "/api/v1/admin/buildings:",
        "admin/openapi-admin.yaml#/paths/~1api~1v1~1admin~1buildings",
        "/api/v1/admin/rooms:",
        "admin/openapi-admin.yaml#/paths/~1api~1v1~1admin~1rooms",
):
    if token not in root_spec:
        raise AssertionError(f"根 OpenAPI 缺少楼栋/房间路径引用：{token}")

for token in (
        "operationId: createBuilding",
        "#/components/schemas/BuildingRequest",
        "operationId: createRoom",
        "#/components/schemas/RoomCreateRequest",
        "BuildingRequest:",
        "required: [buildingCode, buildingName, gender, educationLevelScope, residentScope, floorCount, reason]",
        "RoomCreateRequest:",
        "required: [buildingId, floorNumber, roomNumber, capacity, gender, educationLevelScope, residentScope, operationalStatus, reason]",
):
    if token not in admin_spec:
        raise AssertionError(f"管理端 OpenAPI 缺少楼栋/房间创建契约：{token}")

for token in (
        "import com.wust.dormitory.model.dto.BuildingRequest;",
        "import com.wust.dormitory.model.dto.RoomCreateRequest;",
        "createBuilding(BuildingRequest request)",
        "createRoom(RoomCreateRequest request)",
        "roomManagementService.createBuilding",
        "roomManagementService.createRoom",
):
    if token not in controller:
        raise AssertionError(f"AdminController 与生成契约不同步：{token}")

for token in (
        "public List<Map<String, Object>> buildings()",
        "public long createBuilding(BuildingCommand command",
        "public long createRoom(RoomCreateCommand command",
        "public record BuildingCommand(",
        "public record RoomCreateCommand(",
        "BUILDING_CODE_DUPLICATE",
        "ROOM_NUMBER_DUPLICATE",
):
    if token not in service:
        raise AssertionError(f"RoomManagementService 缺少楼栋/房间能力：{token}")

print("Admin building and room OpenAPI/controller/service contract passed")
