#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/ReferenceDataCacheService.java"
ADMIN = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    require(CACHE.exists(), "缺少 ReferenceDataCacheService")
    cache = CACHE.read_text(encoding="utf-8")
    admin = ADMIN.read_text(encoding="utf-8")

    for token in (
        "StringRedisTemplate",
        "ApplicationReadyEvent",
        "dorm:catalog:majors:",
        "adminCatalogMapper.findMajors",
        "invalidateMajors",
    ):
        require(token in cache, f"静态目录缓存缺少关键实现：{token}")
    require("try" in cache and "catch (RuntimeException" in cache,
            "Redis缓存必须失败开放，Redis异常不得阻断MySQL回源")
    require("public List<Map<String, Object>> majors(Boolean enabled)" in cache,
            "缓存服务必须提供专业目录读穿接口")
    require("referenceDataCacheService.majors(enabled)" in admin,
            "AdminService.majors 必须使用读穿缓存")
    save_major = admin[admin.index("public long saveMajor"):admin.index("public Map<String, Object> students")]
    require(save_major.count("referenceDataCacheService.invalidateMajors()") >= 2,
            "新增和修改专业成功后都必须失效专业目录缓存")
    for forbidden in ("room_assignment", "bed_hold", "assignment_status", "activeResident"):
        require(forbidden not in cache,
                f"静态目录缓存不得缓存实时住宿/选床事实：{forbidden}")

    print("Reference data Redis cache contract: OK")


if __name__ == "__main__":
    main()
