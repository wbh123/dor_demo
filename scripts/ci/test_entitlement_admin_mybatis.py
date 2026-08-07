#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/subscription/EntitlementAdminService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/subscription/mapper/EntitlementAdminMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/subscription/EntitlementAdminMapper.xml"
BASELINE = ROOT / "scripts/ci/backend_modularization_baseline.json"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


service = SERVICE.read_text(encoding="utf-8")
require("NamedParameterJdbcTemplate" not in service, "EntitlementAdminService must not depend on JDBC directly")
require("jdbc." not in service, "EntitlementAdminService must not execute JDBC directly")
require("EntitlementAdminMapper" in service, "EntitlementAdminService must delegate persistence to EntitlementAdminMapper")
require(MAPPER.exists(), "EntitlementAdminMapper.java is required")
require(XML.exists(), "EntitlementAdminMapper.xml is required")

mapper = MAPPER.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")
for method in (
    "findFeatures", "findFeatureOverrides", "findQuotas", "findQuotaOverrides", "findAuditLogs",
    "findFeatureEntitlements", "lockFeatureDefinition", "findActiveFeatureOverridesForUpdate",
    "closeActiveFeatureOverrides", "insertFeatureOverride", "insertQuotaOverride",
):
    require(method in mapper, f"mapper method missing: {method}")
    require(f'id="{method}"' in xml, f"mapper XML statement missing: {method}")

upper = xml.upper()
require("SELECT *" not in upper, "entitlement mapper must not use SELECT *")
require("ROW_NUMBER()" in upper, "effective override selection must use a set-based window function")
require("PARTITION BY" in upper and "FEATURE_CODE" in upper, "effective overrides must be partitioned by feature")
require("ACTIVE_OVERRIDE.ID=(" not in upper.replace(" ", ""), "correlated active override lookup must be eliminated")
require("FOR UPDATE" in upper, "feature mutation paths must preserve row locking")
require("useGeneratedKeys=\"true\"" in xml, "override inserts should use generated keys instead of SELECT LAST_INSERT_ID")

baseline = BASELINE.read_text(encoding="utf-8")
require("EntitlementAdminService.java" not in baseline,
        "EntitlementAdminService must be removed from the >300-line baseline after componentization")
print("entitlement admin MyBatis performance contract: OK")
