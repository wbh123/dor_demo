#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    source = ROOT / path
    if not source.exists():
        raise AssertionError(f"缺少第二批主题文件：{path}")
    return source.read_text(encoding="utf-8")


theme_module = read("frontend/src/site/theme.ts")
app = read("frontend/src/App.vue")
login = read("frontend/src/views/LoginView.vue")
toggle = read("frontend/src/components/admin/AdminThemeToggle.vue")
settings = read("frontend/src/views/admin/AdminSiteSettingsView.vue")

# 学校主题由服务端站点配置驱动；浏览器 localStorage 不再是业务事实。
assert "export type SiteTheme = 'blue' | 'green'" in theme_module
assert "applySiteTheme" in theme_module
assert "clearSiteTheme" in theme_module
assert "localStorage" not in toggle
assert "/api/v1/admin/settings/theme" in toggle
assert 'v-model="theme"' in settings

# 登录页复用已读取的公开站点配置应用主题；学校端应用主题，平台端显式清除学校主题。
assert "applySiteTheme" in login
assert "applySiteTheme" in app
assert "clearSiteTheme" in app
assert "startsWith('/platform')" in app

# 第二批恢复自然表单高度，不再用不可见第三行强行补齐登录/激活高度。
assert 'auth-field-spacer' not in login
assert "grid-template-rows:repeat(3" not in login
assert ".auth-fields input{width:100%;height:40px;" in login

print("Server-backed site theme and natural login layout contract passed")
