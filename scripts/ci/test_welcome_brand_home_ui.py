#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[2]
shell=(root/'frontend/src/layouts/AppShell.vue').read_text()
login=(root/'frontend/src/views/LoginView.vue').read_text()
home=(root/'frontend/src/views/student/StudentHomeContent.vue').read_text()
runner=(root/'scripts/ci/run_frontend.sh').read_text()
welcome_logo_large = (
 'welcome-modal-heading-row' in shell
 and 'text-align:left' in shell
 and (
  'width:72px;height:72px;object-fit:contain' in shell
  or 'width:88px;height:88px;object-fit:contain' in shell
 )
)
horizontal_login_brand = all(token in login for token in (
 'assets/logo-title-right.png',
 'legacyBrandLogo',
 'fallbackBrandLogo',
 'brand-image-surface',
 'hero-brand-surface',
 'card-brand-surface',
 'background:#fff',
 'height:64px;object-fit:contain',
 'height:48px;object-fit:contain',
))
checks={
 'sanitized institution defaults': all('示例大学' in text for text in (shell,login,runner)),
 'BASE-safe shell logo': "${publicBase}assets/logo-only.png" in shell and "import.meta.env.BASE_URL" in shell,
 'public demo emblem fallback': "${publicBase}assert/logo-only.png" in shell and 'fallbackLogo' in shell,
 'no root-only logo path': "'/assets/logo-only.png'" not in shell,
 'welcome blocks router': '<RouterView v-if="!auth.welcomeRequired" />' in shell,
 'welcome heading row and content order': welcome_logo_large and all(token in shell for token in ('welcome-modal-heading','welcome-school-logo','welcome-modal-message','welcome-start-button')),
 'horizontal high-contrast login brand': horizontal_login_brand,
 'assignment emphasis': all(token in home for token in ('assignment-building','assignment-room','assignment-bed','font-size:clamp(2.1rem')),
 'phone common modal': '<AppModal :open="showPhoneDialog"' in home,
 'real admin render': 'test_admin_data_render.mjs' in runner,
}
failed=[name for name,ok in checks.items() if not ok]
if failed:
 print('\n'.join('FAIL: '+name for name in failed)); raise SystemExit(1)
print('Welcome, brand, phone and assignment UI contract passed.')
