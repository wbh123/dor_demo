from pathlib import Path
import re

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

# When a platform entitlement is revoked, preserve the school's stored value and make that non-destructive behavior explicit.
preference_path = 'frontend/src/views/admin/AdminPreferencePolicyView.vue'
preference = read(preference_path)
preference = preference.replace(
    "'系统管理员当前未授权；当前实际不生效。'",
    "'系统管理员当前未授权；原有学校设置会保留，但当前实际不生效。仍可正常保存本页其他策略。'",
)
write(preference_path, preference)

# Theme modification permission is entitlement-driven. Keep the page explanatory, but do not expose a duplicate write toggle.
site_path = 'frontend/src/views/platform/PlatformSiteMetadataView.vue'
site = read(site_path)
site = site.replace("const theme = reactive({ studentTheme: 'blue', schoolAdminEditable: false })", "const theme = reactive({ studentTheme: 'blue' })")
site = site.replace("const schoolAdminEditable = ref(false)\n", '')
site = site.replace("  schoolAdminEditable.value = Boolean(data.schoolAdminEditable)\n", '')
site = site.replace("  theme.schoolAdminEditable = Boolean(themeData.schoolAdminEditable)\n", '')
site = site.replace("  return { branding: { schoolName: branding.schoolName }, login: { html: login.html }, schoolAdminEditable: schoolAdminEditable.value, theme: { ...theme }, adminHome: { ...adminHome } }", "  return { branding: { schoolName: branding.schoolName }, login: { html: login.html }, theme: { ...theme }, adminHome: { ...adminHome } }")
site = site.replace('由授权中心或套餐功能决定。', '由授权中心或套餐设置决定。')
site = site.replace('学校管理员是否可以修改学校主题，由授权中心或套餐设置决定。', '是否允许学校管理员修改学校主题，由授权中心或套餐设置决定。')
write(site_path, site)

# Clean OpenAPI write contract: entitlement state is read-only derived data, not a site-metadata write parameter.
openapi_path = 'backend-java/model/src/main/resources/admin/openapi-site-metadata.yaml'
openapi = read(openapi_path)
openapi = re.sub(
    r"    StudentThemeSettingUpdateRequest:\n      type: object\n      required: \[studentTheme, schoolAdminEditable\]\n      properties:\n        studentTheme:\n          type: string\n          minLength: 1\n          maxLength: 16\n        schoolAdminEditable:\n          type: boolean\n",
    '',
    openapi,
    count=1,
)
openapi = openapi.replace('      required: [branding, login, schoolAdminEditable, theme]', '      required: [branding, login, theme]')
openapi = openapi.replace("        schoolAdminEditable:\n          type: boolean\n        theme:\n          $ref: '#/components/schemas/StudentThemeSettingUpdateRequest'", "        theme:\n          $ref: '#/components/schemas/StudentThemeUpdateRequest'")
write(openapi_path, openapi)

# Remove ignored entitlement booleans from Java write commands and generated-controller mapping.
service_path = 'backend-java/server/src/main/java/com/wust/dormitory/admin/SiteMetadataService.java'
service = read(service_path)
service = service.replace('        return new StudentThemeSettingCommand(validateStudentTheme(command.studentTheme()), command.schoolAdminEditable());', '        return new StudentThemeSettingCommand(validateStudentTheme(command.studentTheme()));')
service = service.replace('    public record StudentThemeSettingCommand(String studentTheme, boolean schoolAdminEditable) { }', '    public record StudentThemeSettingCommand(String studentTheme) { }')
service = service.replace('            boolean schoolAdminEditable,\n            StudentThemeSettingCommand theme,', '            StudentThemeSettingCommand theme,')
write(service_path, service)

controller_path = 'backend-java/server/src/main/java/com/wust/dormitory/platform/PlatformSiteMetadataController.java'
controller = read(controller_path)
controller = controller.replace('                Boolean.TRUE.equals(request.getSchoolAdminEditable()),\n                new SiteMetadataService.StudentThemeSettingCommand(\n                        theme.getStudentTheme(),\n                        Boolean.TRUE.equals(theme.getSchoolAdminEditable())),', '                new SiteMetadataService.StudentThemeSettingCommand(\n                        theme.getStudentTheme()),')
write(controller_path, controller)

test_path = 'backend-java/server/src/test/java/com/wust/dormitory/admin/AdminHomeSiteMetadataTest.java'
test = read(test_path)
test = test.replace('                false,\n                new SiteMetadataService.StudentThemeSettingCommand("blue", false),', '                new SiteMetadataService.StudentThemeSettingCommand("blue"),')
write(test_path, test)

# Public app metadata and QR image must both remain reachable without authentication.
replace_required(
    'backend-java/server/src/main/java/com/wust/dormitory/security/SecurityConfig.java',
    '"/api/v1/mobile/app-version",',
    '"/api/v1/mobile/app-version/**",',
)

# Respect Nginx X-Forwarded-* headers so generated absolute QR URLs preserve public HTTPS scheme/host.
application_path = 'backend-java/starter/src/main/resources/application.yaml'
application = read(application_path)
if 'forward-headers-strategy: framework' not in application:
    marker = 'server:\n  port: ${WUST_DORMITORY_SERVER_PORT:8080}\n'
    if marker not in application:
        raise RuntimeError('application.yaml: server marker missing')
    application = application.replace(marker, marker + '  forward-headers-strategy: framework\n', 1)
write(application_path, application)

print('app download UI followups applied')
