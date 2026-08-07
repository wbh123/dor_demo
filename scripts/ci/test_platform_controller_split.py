#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLATFORM = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/platform"
OLD = PLATFORM / "PlatformManagementController.java"

EXPECTED = {
    "PlatformPlanController.java": {
        "services": {"PlanService"},
        "routes": {
            '@GetMapping("/plans")',
            '@GetMapping("/plans/revisions/{revisionId}")',
            '@PostMapping("/plans")',
            '@PostMapping("/plans/revisions/{sourceRevisionId}")',
        },
    },
    "PlatformSubscriptionController.java": {
        "services": {"SubscriptionService"},
        "routes": {
            '@GetMapping("/subscription")',
            '@GetMapping("/subscription/history")',
            '@GetMapping("/subscription/preview")',
            '@PostMapping("/subscription/plan")',
            '@PostMapping("/subscription/status")',
        },
    },
    "PlatformEntitlementController.java": {
        "services": {"EntitlementAdminService", "QuotaService"},
        "routes": {
            '@GetMapping("/features")',
            '@GetMapping("/features/entitlements")',
            '@PutMapping("/features/{featureCode}/state")',
            '@PostMapping("/features/batch-state")',
            '@GetMapping("/feature-overrides")',
            '@PostMapping("/feature-overrides")',
            '@GetMapping("/quotas")',
            '@PostMapping("/quota-overrides")',
            '@GetMapping("/audit")',
        },
    },
}

ALL_PLATFORM_SERVICES = {
    "PlanService",
    "SubscriptionService",
    "EntitlementAdminService",
    "QuotaService",
}

errors: list[str] = []
if OLD.exists():
    errors.append("旧 PlatformManagementController 仍存在")

sources: dict[str, str] = {}
for filename, contract in EXPECTED.items():
    path = PLATFORM / filename
    if not path.exists():
        errors.append(f"缺少拆分后的控制器：{filename}")
        continue
    source = path.read_text(encoding="utf-8")
    sources[filename] = source
    if '@RequestMapping("/api/v1/platform")' not in source:
        errors.append(f"{filename} 未保持 /api/v1/platform 路径前缀")
    for route in sorted(contract["routes"]):
        if route not in source:
            errors.append(f"{filename} 缺少路由：{route}")
    allowed_services = contract["services"]
    for service in sorted(ALL_PLATFORM_SERVICES - allowed_services):
        if f"private final {service} " in source:
            errors.append(f"{filename} 不应依赖 {service}")

all_source = "\n".join(sources.values())
for contract in EXPECTED.values():
    for route in contract["routes"]:
        count = all_source.count(route)
        if count != 1:
            errors.append(f"平台路由必须且只能实现一次：{route}（当前 {count} 次）")

if errors:
    print("平台管理 Controller 拆分契约失败：")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print("platform controller split contract: OK (4 plan + 5 subscription + 9 entitlement operations)")
