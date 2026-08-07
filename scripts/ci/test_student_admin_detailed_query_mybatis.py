#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/StudentAdminService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/StudentAdminMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/StudentAdminMapper.xml"
QUERY = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/model/query/StudentAdminDetailQuery.java"
ROW = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/model/persistence/StudentAdminDetailRow.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    for path in (SERVICE, MAPPER, XML, QUERY, ROW):
        require(path.exists(), f"缺少学生管理详细查询文件：{path.relative_to(ROOT)}")

    service = SERVICE.read_text(encoding="utf-8")
    mapper = MAPPER.read_text(encoding="utf-8")
    xml = XML.read_text(encoding="utf-8")
    normalized = " ".join(xml.upper().split())

    start = service.index("public Map<String, Object> students")
    end = service.index("@Transactional", start)
    body = service[start:end]
    require("studentAdminMapper.countDetailedStudents" in body,
            "StudentAdminService.students 必须委托详细学生计数 Mapper")
    require("studentAdminMapper.findDetailedStudents" in body,
            "StudentAdminService.students 必须委托详细学生列表 Mapper")
    for token in ("jdbc.", "SELECT ", "StringBuilder where", "MapSqlParameterSource"):
        require(token not in body, f"学生详细分页查询不得在 Service 保留数据库实现：{token}")

    for method in ("countDetailedStudents", "findDetailedStudents"):
        require(method in mapper, f"StudentAdminMapper 缺少方法：{method}")
        require(f'id="{method}"' in xml, f"StudentAdminMapper XML 缺少语句：{method}")

    require("WITH FILTERED_STUDENTS AS" in normalized,
            "详细学生列表必须先分页得到 filtered_students")
    require("ROW_NUMBER() OVER ( PARTITION BY RA.STUDENT_ID" in normalized,
            "有效住宿必须使用窗口函数一次确定当前页学生最新记录")
    require("JOIN FILTERED_STUDENTS FS ON FS.ID = RA.STUDENT_ID" in normalized,
            "住宿窗口聚合必须限制在当前分页学生")
    require("ROW_NUMBER() OVER ( PARTITION BY REQUEST.RESIDENCY_ID" in normalized,
            "待确认床位请求必须使用窗口函数一次确定最新记录")
    require("ACTIVE_RA.ID = ( SELECT" not in normalized,
            "禁止恢复逐学生相关子查询获取有效住宿")
    require("PENDING_REQUEST.ID = ( SELECT" not in normalized,
            "禁止恢复逐住宿相关子查询获取待审核请求")
    require("SELECT *" not in normalized,
            "学生管理 Mapper 高频查询禁止 SELECT *")
    require("LIMIT #{LIMIT} OFFSET #{OFFSET}" in normalized,
            "当前对外分页契约仍需保持 limit/offset")

    print("Student admin detailed MyBatis performance contract: OK")


if __name__ == "__main__":
    main()
