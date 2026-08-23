from pathlib import Path

ROOT = Path('private-repo')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


def replace_required(path: str, old: str, new: str) -> None:
    text = read(path)
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f'{path}: expected marker missing: {old[:120]!r}')
    write(path, text.replace(old, new, 1))


# Frontend source-root tests must traverse out of frontend/src before reading backend sources.
replace_required(
    'frontend/src/views/admin/requested-admin-fixes.spec.ts',
    "read('../backend-java/server/src/main/java/com/wust/dormitory/mobile/MobileAppVersionController.java')",
    "read('../../backend-java/server/src/main/java/com/wust/dormitory/mobile/MobileAppVersionController.java')",
)
replace_required(
    'frontend/src/views/admin/requested-admin-fixes.spec.ts',
    "read('../backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java')",
    "read('../../backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java')",
)

# Welcome toolbar needs rich labels, while the editor contract consumes a Record<string,string> of examples.
dashboard_path = 'frontend/src/views/admin/AdminDashboardView.vue'
dashboard = read(dashboard_path)
old_tokens = """const tokenExamples = [
  { token: 'studentName', label: '学生姓名', example: '例如：张三' },
  { token: '学号', label: '学号', example: '例如：202600000001' },
  { token: '专业名称', label: '专业名称', example: '例如：软件工程' },
  { token: '年级', label: '年级', example: '例如：2026级' },
  { token: '培养层次', label: '培养层次', example: '例如：硕士生' },
  { token: '国家或地区', label: '国家或地区', example: '例如：日本' },
]"""
new_tokens = """const tokenOptions = [
  { token: 'studentName', label: '学生姓名', example: '例如：张三' },
  { token: '学号', label: '学号', example: '例如：202600000001' },
  { token: '专业名称', label: '专业名称', example: '例如：软件工程' },
  { token: '年级', label: '年级', example: '例如：2026级' },
  { token: '培养层次', label: '培养层次', example: '例如：硕士生' },
  { token: '国家或地区', label: '国家或地区', example: '例如：日本' },
]
const tokenExamples = Object.fromEntries(tokenOptions.map(({ token, example }) => [token, example])) as Record<string, string>"""
if new_tokens not in dashboard:
    if old_tokens not in dashboard:
        raise RuntimeError('AdminDashboardView: token option block missing')
    dashboard = dashboard.replace(old_tokens, new_tokens, 1)
dashboard = dashboard.replace('v-for="item in tokenExamples"', 'v-for="item in tokenOptions"')
write(dashboard_path, dashboard)

# Component refs expose open()/clear(); remove remaining native HTMLInputElement access patterns.
print_path = 'frontend/src/views/admin/AdminMobilePrintTemplateView.vue'
print_view = read(print_path)
for old, new in [
    ("assetInput.value.value=''", 'assetInput.value.clear()'),
    ("imageInput.value.value=''", 'imageInput.value.clear()'),
    ("assetInput.value.value = ''", 'assetInput.value.clear()'),
    ("imageInput.value.value = ''", 'imageInput.value.clear()'),
    ('imageInput?.click()', 'imageInput?.open()'),
    ('assetInput?.click()', 'assetInput?.open()'),
]:
    print_view = print_view.replace(old, new)
write(print_path, print_view)

# Keep the existing authorization wording required by the site-theme governance contract.
replace_required(
    'frontend/src/views/platform/PlatformSiteMetadataView.vue',
    '由授权中心或套餐功能决定。',
    '由授权中心或套餐设置决定。',
)

print('app download UI followups applied')
