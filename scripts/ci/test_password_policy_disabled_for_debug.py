#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def password_property_lines(source: str) -> list[str]:
    names = ("password:", "currentPassword:", "newPassword:")
    return [line.strip() for line in source.splitlines() if line.strip().startswith(names)]


auth_openapi = read("backend-java/model/src/main/resources/auth/openapi-auth.yaml")
platform_openapi = read("backend-java/model/src/main/resources/platform/openapi-platform.yaml")
login_view = read("frontend/src/views/LoginView.vue")
admin_password_view = read("frontend/src/views/admin/AdminPasswordView.vue")
platform_password_view = read("frontend/src/views/platform/PlatformPasswordView.vue")
auth_service = read("backend-java/server/src/main/java/com/wust/dormitory/auth/AuthService.java")
platform_auth_service = read("backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformAuthService.java")

password_lines = password_property_lines(auth_openapi) + password_property_lines(platform_openapi)
assert len(password_lines) >= 7
for line in password_lines:
    assert "minLength" not in line
    assert "maxLength" not in line
    assert "pattern" not in line

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
