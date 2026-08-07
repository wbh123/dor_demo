#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OPENAPI = ROOT / "backend-java/model/src/main/resources/student/openapi-student.yaml"
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentSelectionBootstrapController.java"
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentSelectionBootstrapService.java"
STUDENT_CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java"
VIEW = ROOT / "frontend/src/views/student/RoomListView.vue"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    for path in (OPENAPI, CONTROLLER, SERVICE, STUDENT_CONTROLLER, VIEW):
        require(path.exists(), f"缺少学生选寝首屏聚合文件：{path.relative_to(ROOT)}")

    openapi = OPENAPI.read_text(encoding="utf-8")
    controller = CONTROLLER.read_text(encoding="utf-8")
    service = SERVICE.read_text(encoding="utf-8")
    student_controller = STUDENT_CONTROLLER.read_text(encoding="utf-8")
    view = VIEW.read_text(encoding="utf-8")

    require("/api/v1/student/batches/{batchId}/selection/bootstrap:" in openapi,
            "OpenAPI 缺少选寝首屏 bootstrap 路由")
    require("tags: [StudentSelectionBootstrap]" in openapi,
            "bootstrap 必须使用独立 tag，禁止继续扩大 StudentController")
    require("operationId: getStudentSelectionBootstrap" in openapi,
            "bootstrap operationId 缺失")
    require("implements StudentSelectionBootstrapApi" in controller,
            "bootstrap 必须使用生成接口的独立 Controller")
    require("StudentSelectionBootstrapApi" not in student_controller,
            "StudentController 不得吸收 bootstrap 接口")

    for token in (
        "teamService.teams(user)",
        "selectionPolicyService.readiness(batchId, user.studentId())",
        "recommendationService.rooms(batchId, user)",
        'result.put("activePersonalTeam"',
        'result.put("selectionReadiness"',
        'result.put("rooms"',
        'result.put("requiresPersonalTeamExit"',
    ):
        require(token in service, f"bootstrap 服务缺少聚合内容：{token}")
    require("teamId == null && activeTeam != null" in service,
            "个人模式存在活动队伍时必须跳过昂贵的房间/准备度查询")

    require("/selection/bootstrap" in view,
            "RoomListView 必须使用 bootstrap 首屏接口")
    initialize_slice = view[view.index("async function initialize"):view.index("function synchronizeRecommendationStrategy")]
    for old_call in (
        "'/api/v1/student/teams'",
        "`/api/v1/student/batches/${batchId}/rooms`",
        "`/api/v1/student/batches/${batchId}/selection-readiness`",
        "Promise.all([api.get",
    ):
        require(old_call not in initialize_slice,
                f"选寝首屏仍存在拆分请求：{old_call}")

    print("Student selection bootstrap contract: OK")


if __name__ == "__main__":
    main()
