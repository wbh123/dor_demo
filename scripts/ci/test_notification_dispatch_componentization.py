#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/notification/NotificationDispatchService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/notification/mapper/NotificationDispatchMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/notification/NotificationDispatchMapper.xml"

if not SERVICE.exists():
    raise AssertionError("缺少 NotificationDispatchService")
service = SERVICE.read_text(encoding="utf-8")

for path in (MAPPER, XML):
    if not path.exists():
        raise AssertionError(f"通知调度必须迁入 MyBatis：{path.relative_to(ROOT)}")

mapper = MAPPER.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")

if "NotificationDispatchMapper" not in service:
    raise AssertionError("NotificationDispatchService 必须依赖 NotificationDispatchMapper")
for token in ("NamedParameterJdbcTemplate", "jdbc.", "SELECT ", "INSERT INTO", "UPDATE notification_"):
    if token in service:
        raise AssertionError(f"通知调度 Service 不得继续持有 SQL/JDBC：{token}")

for method in (
        "findTemplateRevision",
        "insertTask",
        "insertRecipients",
        "findDueTasks",
        "claimTask",
        "markSucceeded",
        "markFailed",
        "cancelScheduledTask",
        "findStatusPage",
        "findPendingRecipients",
        "markRecipientsDelivered",
):
    if method not in mapper or method not in xml:
        raise AssertionError(f"通知调度 Mapper/XML 缺少持久化操作：{method}")

if "FOR UPDATE SKIP LOCKED" not in xml:
    raise AssertionError("通知到期任务领取必须保留 FOR UPDATE SKIP LOCKED")
if "SELECT *" in xml:
    raise AssertionError("通知调度 XML 禁止 SELECT *")

print("Notification dispatch componentization contract: OK")
