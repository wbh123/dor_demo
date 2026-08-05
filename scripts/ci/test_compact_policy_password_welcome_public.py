#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def read(path: str) -> str:
    source = ROOT / path
    content = source.read_text(encoding='utf-8')
    if source.suffix == '.vue':
        for suffix in ('.logic.ts', '.template.html', '.css'):
            companion = source.with_name(f'{source.stem}{suffix}')
            if companion.exists():
                content += '\n' + companion.read_text(encoding='utf-8')
    return content

density = read('frontend/src/admin-density-refinement.css').replace(' ', '').replace('\n', '')
confirm = read('frontend/src/components/modal/AppConfirmDialog.vue')
shell = read('frontend/src/layouts/AppShell.vue')
policy = read('backend-java/server/src/main/java/com/wust/dormitory/selection/SelectionPolicyService.java')
auth = read('backend-java/server/src/main/java/com/wust/dormitory/auth/AuthService.java')
password_view = read('frontend/src/views/admin/AdminPasswordView.vue')
contract = read('backend-java/model/src/main/resources/auth/openapi-auth.yaml')

assert 'grid-template-columns:repeat(4,minmax(0,1fr))' in density
assert '.page-container{width:100%!important;max-width:none!important;margin:0!important;' in density
assert '.content-column,.content-column.narrow{width:100%!important;max-width:none!important;margin:0!important;' in density
assert 'displaySymbol' in confirm and '已完成发布准备' in confirm
assert '<AppModal' in shell and 'welcome-modal-content' in shell
assert '.app-modal-surface:has(.welcome-modal-content)' in density and 'order:-1' in density
assert 'upsertPolicySetting' in policy and 'updated_by=:updatedBy' in policy
assert 'private void ensure()' not in policy
assert 'newPassword.length() < 4' in auth
assert 'newPassword.value.length >= 4' in password_view
assert 'newPassword: { type: string, minLength: 4' in contract
assert 'password: { type: string, minLength: 4' in contract
print('Public compact policy password welcome contracts passed')
