#!/usr/bin/env python3
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def password_inputs(source: str) -> list[str]:
    return re.findall(r'<input\b[^>]*\btype="password"[^>]*>', source, flags=re.IGNORECASE)

login_view = read("frontend/src/views/LoginView.vue")
admin_password_view = read("frontend/src/views/admin/AdminPasswordView.vue")
platform_password_view = read("frontend/src/views/platform/PlatformPasswordView.vue")
auth_service = read("backend-java/server/src/main/java/com/wust/dormitory/auth/AuthService.java")
platform_auth_service = read("backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformAuthService.java")

# 调试阶段：所有前端密码字段只保留 required，不做长度/字符复杂度限制。
for input_tag in password_inputs(login_view + admin_password_view + platform_password_view):
    lower = input_tag.lower()
    assert "minlength=" not in lower
    assert "maxlength=" not in lower
    assert "pattern=" not in lower

for obsolete in (
    "至少8位", "至少12位", "12至72位", "包含大写字母", "包含小写字母", "包含数字", "包含特殊字符"
):
    assert obsolete not in login_view + admin_password_view + platform_password_view

assert "newPassword == null || newPassword.isBlank()" in auth_service
assert "password == null || password.isBlank()" in auth_service
assert "PASSWORD_POLICY_INVALID" not in auth_service
assert "newPassword.matches(" not in auth_service
assert "password == null || password.isBlank()" in platform_auth_service
assert "PASSWORD_WEAK" not in platform_auth_service
assert "PASSWORD_NOT_CHANGED" not in platform_auth_service
assert "password.matches(" not in platform_auth_service

# 登录/学生激活共用同高表单骨架：登录补第三行占位，激活行距更紧凑。
assert 'class="auth-fields login-fields"' in login_view
assert 'class="auth-field-spacer"' in login_view
assert 'class="auth-fields activate-fields"' in login_view
assert ".auth-form-frame{min-height:" in login_view
assert ".activate-fields{gap:" in login_view

# 左右两个校徽承载区域取消边框和阴影，图片左对齐、不拉伸。
assert ".brand-image-surface{display:flex;align-items:center;justify-content:flex-start;background:transparent;border:0;box-shadow:none;overflow:hidden}" in login_view
for class_name in (".hero-school-logo", ".form-school-logo"):
    assert f"{class_name}{{display:block;width:auto;max-width:100%;" in login_view
    assert "object-fit:contain;object-position:left center" in login_view

print("Debug login layout and password policy contract passed")
