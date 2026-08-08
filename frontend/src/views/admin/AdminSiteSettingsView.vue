<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AdminThemeToggle from '../../components/admin/AdminThemeToggle.vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'
import { normalizeSiteTheme, type SiteTheme } from '../../site/theme'

const state = reactive({ html: '', imageUrl: '' })
const editable = ref(false)
const theme = ref<SiteTheme>('blue')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')
const { translateError } = useI18n()

const previewHtml = computed(() => `<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;min-height:100%;overflow:hidden;font-family:Inter,"Microsoft YaHei",sans-serif;color:#fff;background:transparent}body{display:grid;align-content:center;gap:14px;padding:10px 4px;box-sizing:border-box}h1,h2,h3,p{margin:0}h1{font-size:clamp(30px,4vw,54px);line-height:1.08}p{max-width:720px;font-size:15px;line-height:1.75;color:rgba(255,255,255,.86)}img{display:block;max-width:100%;max-height:260px;object-fit:contain}</style></head><body>${state.imageUrl ? `<img src="${escapeAttribute(state.imageUrl)}" alt="登录页展示图片">` : ''}${state.html}</body></html>`)

onMounted(load)
async function load() {
  loading.value = true; error.value = ''; message.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>('/api/v1/admin/settings/login-page')
    const data = (response.data.data ?? {}) as DataObject
    const login = (data.login ?? {}) as DataObject
    state.html = String(login.html ?? '')
    state.imageUrl = String(login.imageUrl ?? '')
    editable.value = Boolean(data.editable)
    theme.value = normalizeSiteTheme(data.theme)
  } catch (reason) { error.value = translateError(reason) }
  finally { loading.value = false }
}
async function save() {
  if (!editable.value || saving.value) return
  saving.value = true; error.value = ''; message.value = ''
  try {
    await api.put('/api/v1/admin/settings/login-page', { html: state.html.trim(), imageUrl: state.imageUrl.trim() })
    message.value = '登录页左侧展示内容已保存。'
  } catch (reason) { error.value = translateError(reason) }
  finally { saving.value = false }
}
function escapeAttribute(value: string) {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
</script>

<template>
  <div class="content-column">
    <header class="page-title"><span class="eyebrow">界面与登录页</span><h2>站点展示设置</h2><p>主题设置统一作用于管理端、学生端与登录页；登录页内容需系统管理员授权后才能修改。</p></header>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>
    <section class="panel"><div class="section-head"><div><span class="eyebrow">界面主题</span><h3>全系统主题</h3></div></div><AdminThemeToggle v-model="theme" /></section>
    <section class="panel">
      <div class="section-head split-title"><div><span class="eyebrow">登录页左侧</span><h3>内置 HTML 展示区域</h3><p>内容在隔离的内置框架中展示，不显示外部边框和滚动条。</p></div><span class="status-chip" :class="{success:editable}">{{ editable ? '已授权修改' : '等待系统管理员授权' }}</span></div>
      <p v-if="loading" class="empty-state">正在读取登录页设置…</p>
      <div v-else class="login-content-editor-grid">
        <div class="form-grid">
          <label class="span-2"><span>展示图片路径或地址</span><input v-model.trim="state.imageUrl" class="input" :disabled="!editable" placeholder="/assets/login-side.png，可留空" /></label>
          <label class="span-2"><span>HTML 内容</span><textarea v-model="state.html" class="input html-editor" :disabled="!editable" rows="12" placeholder="可使用标题、段落、列表等基础 HTML"></textarea></label>
          <button class="button primary span-2" :disabled="!editable || saving" @click="save">{{ saving ? '正在保存…' : '保存登录页内容' }}</button>
        </div>
        <div class="login-frame-preview"><iframe title="登录页左侧预览" :srcdoc="previewHtml" sandbox="" scrolling="no"></iframe></div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-content-editor-grid{display:grid;grid-template-columns:minmax(0,.9fr) minmax(360px,1.1fr);gap:18px;align-items:stretch}.html-editor{min-height:240px;resize:vertical;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;line-height:1.55}.login-frame-preview{min-height:360px;padding:24px;border-radius:18px;background:linear-gradient(145deg,#102c64,#17417f 58%,#2159a7);overflow:hidden}.login-frame-preview iframe{display:block;width:100%;height:100%;min-height:312px;border:0;background:transparent}.status-chip.success{color:#17664f;background:#e8f8f2}@media(max-width:900px){.login-content-editor-grid{grid-template-columns:1fr}}
</style>
