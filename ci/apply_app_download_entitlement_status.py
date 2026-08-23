from pathlib import Path

ROOT = Path('private-repo')
PATH = ROOT / 'frontend/src/views/platform/PlatformSiteMetadataView.vue'

text = PATH.read_text(encoding='utf-8')

if 'const themeSchoolAdminEditable = ref(false)' not in text:
    marker = "const theme = reactive({ studentTheme: 'blue' })\n"
    if marker not in text:
        raise RuntimeError('PlatformSiteMetadataView: theme state marker missing')
    text = text.replace(marker, marker + 'const themeSchoolAdminEditable = ref(false)\n', 1)

if 'themeSchoolAdminEditable.value = Boolean(themeData.schoolAdminEditable)' not in text:
    marker = "  theme.studentTheme = String(themeData.studentTheme) === 'green' ? 'green' : 'blue'\n"
    if marker not in text:
        raise RuntimeError('PlatformSiteMetadataView: theme metadata marker missing')
    text = text.replace(
        marker,
        marker + '  themeSchoolAdminEditable.value = Boolean(themeData.schoolAdminEditable)\n',
        1,
    )

old = '<div class="authorization-hint"><strong>权限已迁移</strong><span>是否允许学校管理员修改学校主题，由授权中心或套餐设置决定。</span></div>'
new = '<div class="authorization-hint"><strong>{{ themeSchoolAdminEditable ? \'已授权\' : \'未授权\' }}</strong><span>是否允许学校管理员修改学校主题，由授权中心或套餐设置决定。本页仅展示当前授权状态，不在此处修改授权。</span></div>'
if new not in text:
    if old not in text:
        raise RuntimeError('PlatformSiteMetadataView: authorization hint marker missing')
    text = text.replace(old, new, 1)

# Guard the architectural boundary: entitlement state is display-only and must never re-enter the write payload.
payload_start = text.index('function updatePayload()')
payload_end = text.index('function validateImage', payload_start)
payload = text[payload_start:payload_end]
if 'schoolAdminEditable' in payload:
    raise RuntimeError('PlatformSiteMetadataView: entitlement state leaked back into write payload')

PATH.write_text(text, encoding='utf-8')
print('read-only school theme entitlement status applied')
