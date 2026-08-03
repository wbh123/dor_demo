#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
OPENAPI_ROOT = ROOT / "backend-java/model/src/main/resources/openapi-interface.yaml"
OPENAPI_DIR = OPENAPI_ROOT.parent


def require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def validate_openapi(errors: list[str]) -> int:
    require(OPENAPI_ROOT.is_file(), "missing OpenAPI root contract", errors)
    if not OPENAPI_ROOT.is_file():
        return 0

    yaml_files = sorted(OPENAPI_DIR.rglob("*.yaml"))
    path_pattern = re.compile(r"^\s{2}(/api/v1/[^:]+):\s*$", re.MULTILINE)
    ref_pattern = re.compile(r"\$ref:\s*['\"]?([^'\"\s#]+)#")
    paths: set[str] = set()

    for yaml_file in yaml_files:
        text = yaml_file.read_text(encoding="utf-8")
        paths.update(path_pattern.findall(text))
        for target in ref_pattern.findall(text):
            resolved = (yaml_file.parent / target).resolve()
            require(
                resolved.is_file(),
                f"broken OpenAPI reference: {yaml_file.relative_to(ROOT)} -> {target}",
                errors,
            )

    critical_paths = {
        "/api/v1/auth/login",
        "/api/v1/auth/activate",
        "/api/v1/admin/batches",
        "/api/v1/student/profile",
        "/api/v1/student/batches/{batchId}/questionnaire",
        "/api/v1/student/batches/{batchId}/beds/{bedId}/hold",
        "/api/v1/student/batches/{batchId}/teams/{teamId}/confirm",
        "/api/v1/realtime/batches/{batchId}/rooms/{roomId}",
        "/api/v1/platform/subscription",
    }
    for path in sorted(critical_paths):
        require(path in paths, f"missing critical OpenAPI path: {path}", errors)
    require(len(paths) >= 80, f"OpenAPI path count unexpectedly low: {len(paths)}", errors)
    return len(paths)


def validate_fixed_questionnaire(errors: list[str]) -> None:
    service = read(
        "backend-java/server/src/main/java/com/wust/dormitory/admin/BatchCreationService.java"
    )
    require(
        "version_code='SYSTEM-PREFERENCE-V1'" in service,
        "batch creation does not bind the fixed questionnaire code",
        errors,
    )
    require(
        '"BUILTIN_QUESTIONNAIRE_MISSING"' in service,
        "missing stable fixed-questionnaire error code",
        errors,
    )
    require(
        'result.put("questionnaireType", "BUILTIN_FIXED")' in service,
        "batch response does not identify the fixed questionnaire type",
        errors,
    )
    require(
        "version_status='PUBLISHED'" not in service,
        "batch creation still selects the latest published questionnaire",
        errors,
    )
    require(
        "数据库完整性修复" not in service,
        "public business error leaks private database recovery instructions",
        errors,
    )


def validate_frontend(errors: list[str]) -> None:
    shell = read("frontend/src/layouts/AppShell.vue")
    login = read("frontend/src/views/LoginView.vue")
    router = read("frontend/src/router/index.ts")
    vite_config = read("frontend/vite.config.ts")
    env_example = read(".env.example")
    package_json = read("frontend/package.json")

    require(
        "VITE_INSTITUTION_NAME" in shell,
        "application shell does not use configurable institution display name",
        errors,
    )
    require(
        "VITE_INSTITUTION_NAME" in login,
        "login page does not use configurable institution display name",
        errors,
    )
    require(
        "showBuiltinQuestionnaireNotice" in shell
        and "BUILT-IN PREFERENCE QUESTIONNAIRE" in shell,
        "administrator batch page does not expose the fixed-questionnaire notice",
        errors,
    )
    require(
        "loadEnv" in vite_config and re.search(r"envDir:\s*['\"]\.\.['\"]", vite_config),
        "Vite does not load the repository-root environment file",
        errors,
    )
    require(
        "VITE_DEV_SERVER_PORT" in vite_config
        and "VITE_BACKEND_PROXY_TARGET" in vite_config
        and "VITE_ALLOWED_HOSTS" in vite_config,
        "Vite development settings are not fully environment-driven",
        errors,
    )
    for variable in (
        "VITE_INSTITUTION_NAME",
        "VITE_DEV_SERVER_PORT",
        "VITE_BACKEND_PROXY_TARGET",
        "VITE_ALLOWED_HOSTS",
        "WUST_DORMITORY_CORS_ALLOWED_ORIGIN_PATTERNS",
    ):
        require(variable in env_example, f"missing public environment variable: {variable}", errors)

    has_admin_batches_path = re.search(
        r"path:\s*['\"]admin/batches['\"]",
        router,
    )
    has_admin_batches_name = re.search(
        r"name:\s*['\"]admin-batches['\"]",
        router,
    )
    require(
        bool(has_admin_batches_path and has_admin_batches_name),
        "administrator batch route is missing or renamed",
        errors,
    )
    require(
        '"generate:api"' in package_json and "openapi-typescript" in package_json,
        "frontend API types are not generated from the OpenAPI contract",
        errors,
    )


def validate_security_configuration(errors: list[str]) -> None:
    security = read(
        "backend-java/server/src/main/java/com/wust/dormitory/security/SecurityConfig.java"
    )
    require(
        "WUST_DORMITORY_CORS_ALLOWED_ORIGIN_PATTERNS" in security,
        "backend CORS origin patterns are not environment-driven",
        errors,
    )
    require(
        "parseOriginPatterns" in security,
        "backend CORS origin parsing is missing",
        errors,
    )
    require(
        re.search(r"vicp[.]fun", security) is None,
        "backend security configuration contains a hard-coded tunnel domain",
        errors,
    )


def validate_test_inventory(errors: list[str]) -> int:
    tests = sorted(
        (ROOT / "backend-java/server/src/test/java").rglob("*Test.java")
    )
    expected = {
        "AuthTokenSerializationTest.java",
        "BedScopeGuardTest.java",
        "BatchCreationServiceTest.java",
        "BatchSelectionModeGuardTest.java",
        "TeamCategoryGuardTest.java",
        "BedSelectionEligibilityGuardTest.java",
        "SecurityConfigTest.java",
    }
    names = {path.name for path in tests}
    for name in sorted(expected):
        require(name in names, f"missing core regression test: {name}", errors)
    return len(tests)


def main() -> int:
    errors: list[str] = []
    path_count = validate_openapi(errors)
    validate_fixed_questionnaire(errors)
    validate_frontend(errors)
    validate_security_configuration(errors)
    test_count = validate_test_inventory(errors)

    if errors:
        print("System contract validation failed:", file=sys.stderr)
        for error in sorted(set(errors)):
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "System contract validation passed: "
        f"{path_count} OpenAPI paths, {test_count} backend test classes"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
