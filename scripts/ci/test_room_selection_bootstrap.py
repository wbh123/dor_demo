#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OPENAPI = ROOT / "backend-java/model/src/main/resources/student/openapi-room-selection-bootstrap.yaml"
ROOT_OPENAPI = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomSelectionBootstrapController.java"
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomSelectionBootstrapService.java"
VIEW = ROOT / "frontend/src/views/student/RoomDetailView.vue"

for path in (OPENAPI, CONTROLLER, SERVICE):
    if not path.exists():
        raise AssertionError(f"缺少具体寝室选床 bootstrap 文件：{path.relative_to(ROOT)}")

openapi = OPENAPI.read_text(encoding="utf-8")
root_openapi = ROOT_OPENAPI.read_text(encoding="utf-8")
controller = CONTROLLER.read_text(encoding="utf-8")
service = SERVICE.read_text(encoding="utf-8")
view = VIEW.read_text(encoding="utf-8")

path = "/api/v1/student/batches/{batchId}/rooms/{roomId}/selection/bootstrap"
if path not in openapi or "StudentRoomSelectionBootstrap" not in openapi:
    raise AssertionError("必须使用独立 StudentRoomSelectionBootstrap OpenAPI tag")
if "openapi-room-selection-bootstrap.yaml" not in root_openapi:
    raise AssertionError("根 OpenAPI 未注册具体寝室 bootstrap")
if "implements StudentRoomSelectionBootstrapApi" not in controller:
    raise AssertionError("具体寝室 bootstrap Controller 必须实现生成接口")
if "studentService.room(batchId, roomId, user)" not in service:
    raise AssertionError("bootstrap 必须复用现有房间/床位实时读取逻辑")
if "teamSelectionMemberService.confirmedMembers" not in service:
    raise AssertionError("组队 bootstrap 必须聚合已确认成员")
if "serverTime" not in service:
    raise AssertionError("bootstrap 必须返回服务端时间用于倒计时/状态同步")

if "/selection/bootstrap" not in view:
    raise AssertionError("RoomDetailView 首屏必须使用 bootstrap")
if "/selection-members" in view:
    raise AssertionError("RoomDetailView 不得再单独请求队伍成员")
old_room_request = "`/api/v1/student/batches/${batchId}/rooms/${roomId}`"
if old_room_request in view:
    raise AssertionError("RoomDetailView 不得再单独请求旧房间详情作为首屏")

print("Room selection bootstrap contract: OK")
