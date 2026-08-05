#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def schema_block(source: str, name: str, next_name: str) -> str:
    return source.split(f"    {name}:\n", 1)[1].split(f"    {next_name}:\n", 1)[0]


auth_openapi = read("backend-java/model/src/main/resources/auth/openapi-auth.yaml")
platform_openapi = read("backend-java/model/src/main/resources/platform/openapi-platform.yaml")
login_view = read("frontend/src/views/LoginView.vue")
admin_password_view = read("frontend/src/views/admin/AdminPasswordView.vue")
platform_password_view = read("frontend/src/views/platform/PlatformPasswordView.vue")
auth_service = read("backend-java/server/src/main/java/com/wust/dormitory/auth/AuthService.java")
platform_auth_service = read("backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformAuthService.java")

for block in (
    schema_block(auth_openapi, "LoginRequest", "ActivateRequest"),
    schema_block(auth_openapi, "ActivateRequest", "ChangePasswordRequest"),
    schema_block(auth_openapi, "ChangePasswordRequest", "WelcomeData"),
    schema_block(platform_openapi, "PlatformLoginRequest", "PlatformLoginResponse"),
    schema_block(platform_openapi, "PlatformPasswordChangeRequest", "PlanCreateRequest"),
):
    assert "minLength" not in block
    assert "maxLength" not in block
    assert "pattern" not in block

for view in (login_view, admin_password_view, platform_password_view):
    assert 'minlength=' not in view.lower()
    assert 'maxlength=' not in view.lower()

for obsolete in ("至少8位", "至少12位", "4至72位", "12至72位", "包含大写字母", "包含小写字母", "包含数字", "包含特殊字符"):
    assert obsolete not in login_view + admin_password_view + platform_password_view

assert "newPassword == null || newPassword.isBlank()" in auth_service
assert "PASSWORD_POLICY_INVALID" not in auth_service
assert "password == null || password.isBlank()" in platform_auth_service
assert "PASSWORD_WEAK" not in platform_auth_service
assert "PASSWORD_NOT_CHANGED" not in platform_auth_service
assert "password.matches(" not in platform_auth_service
assert "调试阶段仅要求密码非空" in admin_password_view
assert "调试阶段仅要求密码非空" in platform_password_view

print("Password policy is fully disabled for the debugging period")
