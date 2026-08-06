#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/BatchCatalogMapper.java"
ROW = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/model/persistence/BatchCatalogRow.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/BatchCatalogMapper.xml"
TEST = ROOT / "backend-java/server/src/test/java/com/wust/dormitory/admin/BatchCatalogQueryServiceTest.java"

for path in (SERVICE, MAPPER, ROW, XML, TEST):
    if not path.exists():
        raise AssertionError(f"缺少批次目录 MyBatis 文件：{path.relative_to(ROOT)}")

service = SERVICE.read_text(encoding="utf-8")
mapper = MAPPER.read_text(encoding="utf-8")
row = ROW.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")

if "batchCatalogMapper.findBatches" not in service:
    raise AssertionError("AdminService.batches 必须委托 BatchCatalogMapper")
start = service.index("public List<Map<String, Object>> batches")
next_transaction = service.index("@Transactional", start)
body = service[start:next_transaction]
for forbidden in ("jdbc.", "SELECT ", "SELECT sb.*"):
    if forbidden in body:
        raise AssertionError(f"AdminService.batches 不得保留：{forbidden}")

if "@Mapper" not in mapper or "@Select" in mapper:
    raise AssertionError("BatchCatalogMapper 必须使用 XML SQL，禁止 SQL 注解")
if 'namespace="com.wust.dormitory.admin.mapper.BatchCatalogMapper"' not in xml:
    raise AssertionError("BatchCatalogMapper XML namespace 不匹配")
if "SELECT *" in xml or "sb.*" in xml:
    raise AssertionError("批次 Mapper XML 必须显式列出字段")
if "ORDER BY sb.created_at DESC, sb.id DESC" not in xml:
    raise AssertionError("批次列表必须使用稳定倒序")

required_columns = (
    "sb.id",
    "sb.batch_code",
    "sb.batch_name",
    "sb.batch_status",
    "sb.selection_mode",
    "sb.separate_student_categories",
    "sb.questionnaire_version_id",
    "sb.matching_weight_scheme_id",
    "sb.rule_template_id",
    "sb.start_at",
    "sb.end_at",
    "sb.hold_duration_seconds",
    "sb.hold_renewal_limit",
    "sb.allow_team",
    "sb.team_min_size",
    "sb.team_max_size",
    "sb.allow_student_random",
    "sb.unselected_strategy",
    "sb.rule_version",
    "sb.created_by",
    "sb.published_at",
    "sb.finished_at",
    "sb.version",
    "sb.created_at",
    "sb.updated_at",
)
for column in required_columns:
    if column not in xml:
        raise AssertionError(f"批次查询遗漏正式字段：{column}")

required_counts = (
    "eligible_count",
    "assigned_count",
    "bed_assigned_count",
    "room_assigned_count",
    "locked_room_count",
    "unconfirmed_bed_resident_count",
)
for field in required_counts:
    if field not in xml:
        raise AssertionError(f"批次查询遗漏统计字段：{field}")
    if f'response.put("{field}"' not in row:
        raise AssertionError(f"批次结果遗漏响应字段：{field}")

if xml.count("FROM batch_student_eligibility") != 1:
    raise AssertionError("资格人数必须按批次一次聚合")
if xml.count("FROM bed_assignment") != 1:
    raise AssertionError("床位结果必须按批次一次聚合")
if xml.count("FROM room_assignment") != 1:
    raise AssertionError("寝室归属与待确认床位必须在同一次聚合中完成")
if xml.count("FROM active_batch_room_lock") != 1:
    raise AssertionError("活动房间锁必须按批次一次聚合")

print("Admin batch catalog MyBatis contract passed")
