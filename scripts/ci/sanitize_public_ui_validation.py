#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]
replacements = {
    root / 'frontend/src/layouts/AppShell.vue': [("'武汉科技大学'", "'示例大学'")],
    root / 'frontend/src/views/LoginView.vue': [("'武汉科技大学'", "'示例大学'")],
    root / 'scripts/ci/run_frontend.sh': [('武汉科技大学', '示例大学'), ('黄家湖校区', '主校区')],
    root / 'scripts/ci/test_welcome_brand_home_ui.py': [
        ("'Wuhan defaults': all('武汉科技大学' in text for text in (shell,login,runner)),", "'sanitized institution defaults': all('示例大学' in text for text in (shell,login,runner)),"),
    ],
    root / 'scripts/ci/test_welcome_brand_room_exchange.py': [
        ('require(shell, "\'/assert/logo-only.png\'", "navigation must use the fixed /assert/logo-only.png asset")',
         'require(shell, "${publicBase}assert/logo-only.png", "navigation must use a BASE_URL-safe public emblem asset")\nrequire(shell, "import.meta.env.BASE_URL", "navigation emblem must support non-root Vite deployment")'),
    ],
}

for path, pairs in replacements.items():
    text = path.read_text(encoding='utf-8')
    for old, new in pairs:
        if old not in text:
            raise RuntimeError(f'missing expected fragment in {path}: {old}')
        text = text.replace(old, new, 1)
    path.write_text(text, encoding='utf-8')
