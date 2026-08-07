<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { platformApi } from '../api'

type PlatformObject = Record<string, unknown>
const branding = reactive({ schoolName: '', squareLogoUrl: '', horizontalLogoUrl: '' })
const login = reactive({ html: '', imageUrl: '' })
const schoolAdminEditable = ref(false)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')

const previewHtml = computed(() => `<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;min-height:100%;overflow:hidden;font-family:Inter,"Microsoft YaHei",sans-serif;color:#fff;background:transparent}body{display:grid;align-content:center;gap:14px;padding:8px;box-sizing:border-box}h1,h2,h3,p{margin:0}h1{font-size:42px}p{font-size:15px;line-height:1.75;color:rgba(255,255,255,.86)}img{display:block;max-width:100%;max-height:220px;object-fit:contain}</style></head><body>${login.imageUrl ? `<img src="${escapeAttribute(login.imageUrl)}" alt="登录页图片">` : ''}${login.html}</body></html>`)

onMounted(load)
async function load() {
  loading.value = true; error.value = ''; message.value = ''
  try {
    const response = await platformApi.get('/api/v1/platform/site-metadata')
    const data = (response.data?.data ?? {}) as PlatformObject
    const brandingData = (data.branding ?? {}) as PlatformObject
    const loginData = (data.login ?? {}) as PlatformObject
    branding.schoolName = String(brandingData.schoolName ?? '')
    branding.squareLogoUrl = String(brandingData.squareLogoUrl ?? '')
    branding.horizontalLogoUrl = String(brandingData.horizontalLogoUrl ?? '')
    login.html = String(loginData.html ?? '')
    login.imageUrl = String(loginData.imageUrl ?? '')
    schoolAdminEditable.value = Boolean(data.schoolAdminEditable)
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '读取站点元数据失败' }
  finally { loading.value = false }
}
async function save() {
  if (saving.value) return
  saving.value = true; error.value = ''; message.value = ''
  try {
    await platformApi.put('/api/v1/platform/site-metadata', {
      branding: { ...branding }, login: { ...login }, schoolAdminEditable: schoolAdminEditable.value,
    })
    message.value = '学校元数据与登录页配置已保存。'
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '保存失败' }
  finally { saving.value = false }
}
function escapeAttribute(value: string) {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
</script>

<template>
  <section class="platform-content-column">
    <header class="platform-page-title"><div><span>站点元数据</span><h1>学校与登录页展示</h1><p>统一维护学校名称、两种校徽以及登录页左侧内容，并决定学校管理员是否拥有编辑权限。</p></div></header>
    <p v-if="error" class="platform-alert error">{{ error }}</p><p v-if="message" class="platform-alert success">{{ message }}</p>
    <p v-if="loading" class="platform-panel">正在读取站点配置…</p>
    <template v-else>
      <section class="platform-panel form-section"><h2>学校元数据</h2><div class="metadata-grid"><label><span>学校名称</span><input v-model.trim="branding.schoolName" /></label><label><span>正方形校徽地址</span><input v-model.trim="branding.squareLogoUrl" placeholder="/assets/logo-only.png" /></label><label class="wide"><span>长条形校徽地址</span><input v-model.trim="branding.horizontalLogoUrl" placeholder="/assets/logo-title-right.png" /></label></div><div class="logo-preview-row"><figure><img :src="branding.squareLogoUrl" alt="正方形校徽预览" /><figcaption>正方形校徽</figcaption></figure><figure class="wide-logo"><img :src="branding.horizontalLogoUrl" alt="长条形校徽预览" /><figcaption>长条形校徽</figcaption></figure></div></section>
      <section class="platform-panel form-section"><div class="section-title"><div><h2>登录页左侧内容</h2><p>学校管理员只有在下方授权打开后才能修改此区域。</p></div><label class="permission-toggle"><input v-model="schoolAdminEditable" type="checkbox" /><span>允许学校管理员修改</span></label></div><div class="login-editor-grid"><div><label><span>展示图片路径或地址</span><input v-model.trim="login.imageUrl" /></label><label><span>HTML 内容</span><textarea v-model="login.html" rows="12"></textarea></label></div><div class="login-preview"><iframe title="登录页内容预览" :srcdoc="previewHtml" sandbox="" scrolling="no"></iframe></div></div></section>
      <div class="save-row"><button type="button" :disabled="saving" @click="save">{{ saving ? '正在保存…' : '保存全部站点设置' }}</button></div>
    </template>
  </section>
</template>

<style scoped>
.platform-content-column{display:grid;gap:18px}.platform-page-title span{color:#5e73a1;font-size:.72rem;font-weight:800;letter-spacing:.12em}.platform-page-title h1{margin:6px 0 8px;font-size:1.7rem}.platform-page-title p,.section-title p{margin:0;color:#69758b}.platform-panel{padding:22px;border:1px solid #dde4ef;border-radius:16px;background:#fff;box-shadow:0 12px 28px rgba(22,43,82,.05)}.form-section{display:grid;gap:18px}.form-section h2{margin:0;font-size:1.05rem}.metadata-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.metadata-grid .wide{grid-column:1/-1}label{display:grid;gap:7px;color:#46536a;font-size:.82rem;font-weight:700}input,textarea{width:100%;box-sizing:border-box;padding:10px 12px;border:1px solid #ccd6e5;border-radius:10px;background:#fff;color:#172033}textarea{resize:vertical}.logo-preview-row{display:grid;grid-template-columns:minmax(150px,.35fr) minmax(320px,1fr);gap:16px}.logo-preview-row figure{display:grid;place-items:center;gap:8px;min-height:140px;margin:0;padding:16px;border-radius:14px;background:#f6f8fc}.logo-preview-row img{display:block;max-width:100%;max-height:100px;object-fit:contain}.logo-preview-row .wide-logo img{max-height:76px}.logo-preview-row figcaption{color:#69758b;font-size:.75rem}.section-title{display:flex;align-items:center;justify-content:space-between;gap:18px}.permission-toggle{display:flex;align-items:center;gap:8px}.permission-toggle input{width:auto}.login-editor-grid{display:grid;grid-template-columns:minmax(0,.9fr) minmax(360px,1.1fr);gap:18px}.login-editor-grid>div:first-child{display:grid;gap:14px}.login-preview{min-height:340px;padding:22px;border-radius:16px;background:linear-gradient(145deg,#102c64,#17417f 58%,#2159a7);overflow:hidden}.login-preview iframe{width:100%;height:100%;min-height:296px;border:0}.save-row{display:flex;justify-content:flex-end}.save-row button{min-height:42px;padding:0 20px;border:0;border-radius:11px;color:#fff;background:#194a91;font-weight:800;cursor:pointer}.save-row button:disabled{opacity:.55}.platform-alert{padding:12px 14px;border-radius:12px}.platform-alert.error{color:#9f1d1d;background:#fff0f0}.platform-alert.success{color:#17664f;background:#e9f7f1}@media(max-width:860px){.metadata-grid,.login-editor-grid,.logo-preview-row{grid-template-columns:1fr}.section-title{align-items:flex-start;flex-direction:column}}
</style>
