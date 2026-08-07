#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/matching/MatchingSchemeService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/matching/mapper/MatchingSchemeMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/matching/MatchingSchemeMapper.xml"
CACHE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/matching/MatchingSchemePolicyCache.java"
BASELINE = ROOT / "scripts/ci/backend_modularization_baseline.json"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


service = SERVICE.read_text(encoding="utf-8")
require("NamedParameterJdbcTemplate" not in service, "MatchingSchemeService must not depend on JDBC")
require("jdbc." not in service, "MatchingSchemeService must not execute JDBC")
require("MatchingSchemeMapper" in service, "MatchingSchemeService must delegate persistence to a mapper")
require("MatchingSchemePolicyCache" in service, "policyForBatch must use the dedicated matching policy cache")
require(MAPPER.exists(), "MatchingSchemeMapper.java is required")
require(XML.exists(), "MatchingSchemeMapper.xml is required")
require(CACHE.exists(), "MatchingSchemePolicyCache.java is required")

mapper = MAPPER.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")
cache = CACHE.read_text(encoding="utf-8")
for method in (
    "findSchemes", "countSchemeCode", "findScheme", "findSchemeForUpdate",
    "claimVersion", "findLatestRevisionForUpdate", "deactivateAll", "insertScheme",
    "findPolicySchemeIdForBatch", "findPolicyScheme",
):
    require(method in mapper, f"matching scheme mapper method missing: {method}")
    require(f'id="{method}"' in xml, f"matching scheme mapper XML statement missing: {method}")

upper = xml.upper()
require("SELECT *" not in upper, "matching scheme mapper must not use SELECT *")
require("BATCH_USAGE" in upper and "GROUP BY MATCHING_WEIGHT_SCHEME_ID" in upper,
        "scheme usage count must use one pre-aggregated join")
require("SELECT COUNT(*) FROM SELECTION_BATCH" not in upper,
        "scheme list/detail must not use a per-scheme correlated batch count")
require("FOR UPDATE" in upper, "revision creation must preserve row locking")
require("useGeneratedKeys=\"true\"" in xml, "scheme insert must use generated keys")

require("StringRedisTemplate" in cache, "matching policy cache must use Redis")
require('"dorm:matching:scheme:"' in cache, "matching policy cache key prefix is missing")
require("try" in cache and "catch" in cache, "Redis access must fail open to MySQL")
require("findPolicyScheme" in cache, "cache miss must rebuild the policy from MySQL")
for forbidden in ("student_feature", "room_assignment", "bed_assignment", "student_id", "room_id"):
    require(forbidden not in cache.lower(), f"matching policy cache must not contain dynamic fact: {forbidden}")

require("policyCache.policyForBatch" in service, "policyForBatch must delegate to the policy cache")
require("invalidate" in service, "matching scheme changes must invalidate cached scheme configuration")

baseline = BASELINE.read_text(encoding="utf-8")
require("MatchingSchemeService.java" not in baseline,
        "MatchingSchemeService must leave the >300-line baseline")
print("matching scheme MyBatis and Redis cache contract: OK")
