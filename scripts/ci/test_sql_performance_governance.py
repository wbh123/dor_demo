#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomRecommendationService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/mapper/StudentRoomRecommendationMapper.java"
MAPPER_XML = ROOT / "backend-java/server/src/main/resources/mapper/student/StudentRoomRecommendationMapper.xml"


def fail(message: str) -> None:
    raise AssertionError(message)


def main() -> None:
    service = SERVICE.read_text(encoding="utf-8")

    forbidden_service_fragments = {
        "NamedParameterJdbcTemplate": "推荐服务不得直接依赖 JDBC",
        "jdbc.query": "推荐服务不得直接执行 jdbc.query",
        "jdbc.queryForList": "推荐服务不得直接执行 jdbc.queryForList",
        "jdbc.queryForObject": "推荐服务不得直接执行 jdbc.queryForObject",
        "for (Long roomId : policy.roomIdsForBatch(batchId))": "候选房间不得逐房间查询",
        "matchingService.roomScore(batchId": "候选评分不得逐房间重新加载匹配方案",
    }
    for fragment, message in forbidden_service_fragments.items():
        if fragment in service:
            fail(f"{message}: {fragment}")

    required_service_fragments = {
        "StudentRoomRecommendationMapper": "推荐服务必须通过专用 Mapper 读取数据库数据",
        "matchingService.policyForBatch(batchId)": "启用推荐时必须预加载一次匹配方案",
        "matchingService.roomScore(matchingPolicy": "候选评分必须复用预加载匹配方案",
    }
    for fragment, message in required_service_fragments.items():
        if fragment not in service:
            fail(f"{message}: {fragment}")

    if not MAPPER.exists():
        fail("缺少 StudentRoomRecommendationMapper.java")
    if not MAPPER_XML.exists():
        fail("缺少 StudentRoomRecommendationMapper.xml")

    mapper_source = MAPPER.read_text(encoding="utf-8")
    mapper_xml = MAPPER_XML.read_text(encoding="utf-8")
    for annotation in ("@Select", "@Insert", "@Update", "@Delete"):
        if annotation in mapper_source:
            fail(f"推荐 Mapper 禁止使用 SQL 注解: {annotation}")

    normalized = " ".join(mapper_xml.upper().split())
    if "SELECT *" in normalized:
        fail("推荐 Mapper 高频查询禁止 SELECT *")
    if "ROOM_ID IN" not in normalized:
        fail("推荐 Mapper 必须包含按 room_id IN (...) 的批量查询")
    if "GROUP BY" not in normalized or "BED_TYPE" not in normalized:
        fail("推荐 Mapper 必须批量聚合可用床型")

    print("SQL performance governance contract: OK")


if __name__ == "__main__":
    main()
