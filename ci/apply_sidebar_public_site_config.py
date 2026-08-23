from pathlib import Path

ROOT = Path('private-repo')
PATH = ROOT / 'frontend/src/layouts/AppShell.vue'
text = PATH.read_text(encoding='utf-8')

if "import { api } from '../api/client'" not in text:
    marker = "import { useRoute, useRouter } from 'vue-router'\n"
    if marker not in text:
        raise RuntimeError('AppShell: Vue router import marker missing')
    text = text.replace(marker, marker + "import { api } from '../api/client'\n", 1)

old_type = "import type { DataObject } from '../api/types'"
new_type = "import type { DataObject, ObjectSuccessResponse } from '../api/types'"
if new_type not in text:
    if old_type not in text:
        raise RuntimeError('AppShell: API type import marker missing')
    text = text.replace(old_type, new_type, 1)

text = text.replace("import { platformApi } from '../platform/api'\n", '', 1)

old = '''async function loadSchoolNavigationBrand() {
  if (!auth.isBusinessAdmin) return
  try {
    const response = await platformApi.siteMetadata()
    const adminHome = ((response.data ?? {}) as DataObject).adminHome as DataObject | undefined
    productName.value = String(adminHome?.title || productName.value)
    productSubtitle.value = String(adminHome?.subtitle || productSubtitle.value)
  } catch {
    // 站点元数据暂不可用时保留构建期默认标题，导航不受影响。
  }
}'''
new = '''async function loadSchoolNavigationBrand() {
  if (!auth.isBusinessAdmin) return
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/public/site-config')
    const siteConfig = (response.data.data ?? {}) as DataObject
    const adminHome = (siteConfig.adminHome ?? {}) as DataObject
    productName.value = String(adminHome.title || productName.value)
    productSubtitle.value = String(adminHome.subtitle || productSubtitle.value)
  } catch {
    // 公开站点配置暂不可用时保留构建期默认标题，导航不受影响。
  }
}'''
if new not in text:
    if old not in text:
        raise RuntimeError('AppShell: school navigation branding loader marker missing')
    text = text.replace(old, new, 1)

if 'platformApi.siteMetadata()' in text:
    raise RuntimeError('AppShell: business-admin sidebar still depends on platform-admin metadata API')
if "'/api/v1/public/site-config'" not in text:
    raise RuntimeError('AppShell: public site config endpoint missing')

PATH.write_text(text, encoding='utf-8')
print('school sidebar branding now reads public site config')
