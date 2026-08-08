#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    source = ROOT / path
    if not source.exists():
        raise AssertionError(f"缺少第二批必要文件：{path}")
    return source.read_text(encoding="utf-8")


service = read("backend-java/server/src/main/java/com/wust/dormitory/admin/SiteMetadataService.java")
admin_controller = read("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminLoginPageSettingController.java")
platform_controller = read("backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformSiteMetadataController.java")
site_openapi = read("backend-java/model/src/main/resources/admin/openapi-site-metadata.yaml")
app = read("frontend/src/App.vue")
login = read("frontend/src/views/LoginView.vue")
settings = read("frontend/src/views/admin/AdminSiteSettingsView.vue")
toggle = read("frontend/src/components/admin/AdminThemeToggle.vue")
dashboard = read("frontend/src/views/admin/AdminDashboardView.vue")

# 系统管理员维护学校品牌和授权；学校管理员只维护登录页内容与学校主题。
assert "implements PlatformSiteMetadataApi" in platform_controller
assert "updatePlatformSiteMetadata" in platform_controller
assert "implements AdminSiteMetadataApi" in admin_controller
assert "schoolAdminEditable()" in service
assert "LOGIN_PAGE_CUSTOMIZE_FORBIDDEN" in service
assert "/api/v1/platform/site-metadata:" in site_openapi
assert "/api/v1/admin/settings/login-page:" in site_openapi
assert "/api/v1/admin/settings/theme:" in site_openapi

# 登录页 HTML 必须是受控安全范围；iframe 仍保持最小权限隔离。
assert "validateSafeLoginHtml" in service
assert "LOGIN_HTML_ALLOWED_TAGS" in service
assert "LOGIN_CONTENT_UNSAFE" in service
assert 'sandbox=""' in login and 'scrolling="no"' in login
assert ".login-left-frame{display:block;width:100%;height:100%;min-height:300px;border:0;background:transparent;overflow:hidden}" in login
assert 'sandbox=""' in settings and 'scrolling="no"' in settings

# 校徽保持原比例，登录/激活恢复自然高度。
assert ".hero-school-logo{display:block;width:auto;max-width:100%;height:auto;" in login
assert ".form-school-logo{display:block;width:auto;max-width:100%;height:auto;" in login
assert "object-fit:contain;object-position:left center" in login
assert "auth-field-spacer" not in login
assert "grid-template-rows:repeat(3" not in login

# 学校主题来自服务端；平台端显式清除学校主题。
assert "SITE_THEME" in service
assert "/api/v1/admin/settings/theme" in toggle
assert "localStorage" not in toggle
assert 'v-model="theme"' in settings
assert "applySiteTheme" in login and "applySiteTheme" in app
assert "clearSiteTheme" in app and "startsWith('/platform')" in app

# 欢迎语保存动作已经固定在欢迎语卡片右上角，不在编辑器底部占用额外高度。
assert 'class="button-row welcome-actions"' in dashboard
assert ".welcome-setting-card .welcome-actions{position:absolute;top:18px;right:20px;" in app

print("Second-batch site metadata, theme, login and welcome contracts passed")
