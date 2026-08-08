#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportMutationService.java"
SNAPSHOT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/importworkflow/mapper/ImportMutationSnapshotMapper.java"
ROLLBACK = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/importworkflow/mapper/ImportRollbackMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/importworkflow/ImportMutationMapper.xml"

errors = []
service = SERVICE.read_text(encoding="utf-8")
if "NamedParameterJdbcTemplate" in service or "jdbc." in service or "MapSqlParameterSource" in service:
    errors.append("ImportMutationService 仍直接持有 JDBC/SQL 参数实现")
for path, label in [(SNAPSHOT, "ImportMutationSnapshotMapper"), (ROLLBACK, "ImportRollbackMapper"), (XML, "ImportMutationMapper.xml")]:
    if not path.exists():
        errors.append(f"缺少 {label}")

if SNAPSHOT.exists() and ROLLBACK.exists() and XML.exists():
    snapshot = SNAPSHOT.read_text(encoding="utf-8")
    rollback = ROLLBACK.read_text(encoding="utf-8")
    xml = XML.read_text(encoding="utf-8")
    for method in ["findStudentSnapshotByNumber", "findStudentSnapshot", "findRoomSnapshot", "countActiveResidents"]:
        if method not in snapshot or f'id="{method}"' not in xml:
            errors.append(f"导入快照 Mapper 缺少：{method}")
    for method in [
        "deleteStudentUser", "deleteStudent", "restoreStudent", "restoreUser",
        "deleteRoomBeds", "deleteRoom", "deleteFloorIfEmpty", "deleteBuildingIfEmpty", "restoreRoom",
    ]:
        if method not in rollback or f'id="{method}"' not in xml:
            errors.append(f"导入回滚 Mapper 缺少：{method}")
    if "NOT EXISTS" in xml.upper():
        errors.append("空楼层/楼栋清理应使用反连接条件删除，避免 NOT EXISTS 子查询")
    if "SELECT *" in xml.upper():
        errors.append("ImportMutationMapper.xml 不得使用 SELECT *")

if errors:
    print("import mutation MyBatis contract failed:")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)
print("import mutation MyBatis contract: OK")
