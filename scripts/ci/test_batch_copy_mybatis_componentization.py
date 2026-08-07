#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchCopyService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/BatchCopyMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/BatchCopyMapper.xml"

service = SERVICE.read_text(encoding="utf-8")
for path in (MAPPER, XML):
    if not path.exists():
        raise AssertionError(f"批次复制必须迁入 MyBatis：{path.relative_to(ROOT)}")
mapper = MAPPER.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")

if "BatchCopyMapper" not in service:
    raise AssertionError("BatchCopyService 必须依赖 BatchCopyMapper")
for token in ("NamedParameterJdbcTemplate", "jdbc.", "SELECT ", "INSERT INTO batch_", "UPDATE "):
    if token in service:
        raise AssertionError(f"BatchCopyService 不得保留 SQL/JDBC：{token}")

for method in (
        "findSourceBatchForUpdate",
        "countBatchCode",
        "validateTemplateReferences",
        "findScopeCounts",
        "findUnavailableResources",
        "insertBatch",
        "copyBuildingScope",
        "copyRoomScope",
        "copyBedScope",
):
    if method not in mapper or method not in xml:
        raise AssertionError(f"BatchCopyMapper/XML 缺少操作：{method}")

if "FOR UPDATE" not in xml:
    raise AssertionError("源批次读取必须保留 FOR UPDATE")
if "UNION ALL" not in xml or "LIMIT #{limit}" not in xml:
    raise AssertionError("不可用资源必须集合化为一次 UNION ALL 查询并统一限量")
for table in ("questionnaire_version", "matching_weight_scheme", "batch_rule_template"):
    if xml.count(f"FROM {table}") != 1:
        raise AssertionError(f"模板引用表 {table} 必须只在一次聚合校验中访问")
if "SELECT *" in xml:
    raise AssertionError("批次复制 XML 禁止 SELECT *")

print("Batch copy MyBatis componentization contract: OK")
