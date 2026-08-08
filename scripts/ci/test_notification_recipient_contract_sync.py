from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
openapi = (ROOT / 'backend-java/model/src/main/resources/admin/openapi-governance.yaml').read_text(encoding='utf-8')
aggregate = (ROOT / 'backend-java/model/src/main/resources/openapi-interface.yaml').read_text(encoding='utf-8')
resolver = (ROOT / 'backend-java/server/src/main/java/com/wust/dormitory/notification/NotificationRecipientResolver.java').read_text(encoding='utf-8')
center = (ROOT / 'frontend/src/features/admin-governance/composables/useNotificationCenter.ts').read_text(encoding='utf-8')
dialog = (ROOT / 'frontend/src/components/admin/StudentMessageDialog.vue').read_text(encoding='utf-8')

for field in ['batchIds', 'majorIds', 'buildingIds', 'gradeYears', 'degreeLevels', 'studentCategories']:
    assert field in resolver, f'backend resolver must support {field}'
    assert field in center, f'notification center must send {field}'
    assert field in openapi, f'OpenAPI RecipientCriteria is stale: missing {field}'

for path in [
    '/api/v1/admin/governance/notifications/recipients/count:',
    '/api/v1/admin/governance/notifications/direct:',
]:
    assert path in openapi, f'{path} must be part of governance OpenAPI'
    assert path in aggregate, f'{path} must be wired into aggregate openapi-interface.yaml'

assert 'NotificationPreflightRequest:' in openapi, 'preview must use a request schema that does not require a send reason'
preflight_path = openapi[openapi.index('/api/v1/admin/governance/notifications/preflight:'):]
preflight_path = preflight_path[:preflight_path.index('/api/v1/admin/governance/notifications/schedule:')]
assert 'NotificationPreflightRequest' in preflight_path

preflight_marker = "'/api/v1/admin/governance/notifications/preflight'"
assert preflight_marker in dialog, 'student private-message template mode must support preview'
print('Notification recipient contract synchronization passed')
