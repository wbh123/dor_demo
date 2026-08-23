from pathlib import Path
import re

ROOT = Path('private-repo')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{path}: expected one marker, found {count}: {old[:100]!r}')
    write(path, text.replace(old, new, 1))


def regex_once(path: str, pattern: str, replacement: str, flags=0) -> None:
    text = read(path)
    updated, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f'{path}: regex marker count={count}: {pattern[:120]}')
    write(path, updated)


def add_import(path: str, line: str, after: str) -> None:
    text = read(path)
    if line in text:
        return
    if after not in text:
        raise RuntimeError(f'{path}: import anchor missing: {after}')
    write(path, text.replace(after, after + '\n' + line, 1))


write('frontend/src/components/form/SystemSwitch.vue', r'''<script setup lang="ts">
const props = withDefaults(defineProps<{
  modelValue: boolean
  label: string
  description?: string
  disabled?: boolean
}>(), { description: '', disabled: false })

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
function toggle() {
  if (!props.disabled) emit('update:modelValue', !props.modelValue)
}
</script>

<template>
  <div class="system-switch" :class="{ disabled }">
    <button
      type="button"
      class="system-switch-control"
      role="switch"
      :aria-checked="modelValue"
      :aria-label="label"
      :disabled="disabled"
      :class="{ checked: modelValue }"
      @click="toggle"
    ><span /></button>
    <div class="system-switch-copy">
      <strong>{{ label }}</strong>
      <small v-if="description">{{ description }}</small>
    </div>
  </div>
</template>

<style scoped>
.system-switch{display:flex;align-items:center;gap:14px;min-width:0;padding:14px 16px;border:1px solid var(--border,var(--line));border-radius:14px;background:var(--surface-soft,var(--soft));transition:.18s ease}.system-switch.disabled{opacity:.62}.system-switch-copy{min-width:0;flex:1;display:grid;gap:4px}.system-switch-copy strong{font-weight:700}.system-switch-copy small{color:var(--text-muted,var(--muted));line-height:1.5}.system-switch-control{appearance:none;position:relative;width:50px;height:28px;border:0;border-radius:999px;background:#cbd5e1;flex:0 0 auto;cursor:pointer;transition:.2s}.system-switch-control>span{position:absolute;left:3px;top:3px;width:22px;height:22px;border-radius:50%;background:#fff;box-shadow:0 1px 4px rgba(15,23,42,.18);transition:.2s}.system-switch-control.checked{background:var(--primary)}.system-switch-control.checked>span{transform:translateX(22px)}.system-switch-control:focus-visible{outline:3px solid color-mix(in srgb,var(--primary) 32%,transparent);outline-offset:2px}.system-switch-control:disabled{cursor:not-allowed}
</style>
''')

write('frontend/src/components/form/FileUploadField.vue', r'''<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  label?: string
  hint?: string
  accept?: string
  disabled?: boolean
  fileName?: string
  buttonLabel?: string
}>(), { label: '', hint: '', accept: '', disabled: false, fileName: '', buttonLabel: '选择文件' })
const emit = defineEmits<{ change: [event: Event] }>()
const input = ref<HTMLInputElement | null>(null)
const localName = ref('')
const shownName = computed(() => props.fileName || localName.value)

function open() { if (!props.disabled) input.value?.click() }
function clear() { localName.value = ''; if (input.value) input.value.value = '' }
function changed(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  localName.value = file?.name ?? ''
  emit('change', event)
}
watch(() => props.fileName, value => { if (!value && props.fileName !== undefined) clear() })
defineExpose({ open, clear })
</script>

<template>
  <div class="file-upload-field" :class="{ disabled }">
    <span v-if="label" class="file-upload-label">{{ label }}</span>
    <button type="button" class="file-upload-box" :disabled="disabled" @click="open">
      <span class="file-upload-icon" aria-hidden="true">↑</span>
      <span class="file-upload-copy">
        <strong>{{ shownName || buttonLabel }}</strong>
        <small>{{ shownName ? '点击重新选择文件' : (hint || '点击选择本地文件') }}</small>
      </span>
    </button>
    <small v-if="hint && shownName" class="file-upload-hint">{{ hint }}</small>
    <input ref="input" class="file-upload-native" type="file" :accept="accept" :disabled="disabled" @change="changed" />
  </div>
</template>

<style scoped>
.file-upload-field{display:grid;gap:7px;min-width:0}.file-upload-label{font-size:.78rem;font-weight:700;color:var(--text-muted,var(--muted))}.file-upload-box{appearance:none;width:100%;min-height:64px;display:flex;align-items:center;gap:12px;padding:11px 13px;border:1px dashed color-mix(in srgb,var(--primary) 45%,var(--border,var(--line)));border-radius:12px;background:var(--surface-soft,var(--soft));color:inherit;text-align:left;cursor:pointer;transition:.18s}.file-upload-box:hover:not(:disabled){border-color:var(--primary);background:color-mix(in srgb,var(--primary) 6%,var(--surface-soft,var(--soft)))}.file-upload-icon{display:grid;place-items:center;width:34px;height:34px;border-radius:10px;background:color-mix(in srgb,var(--primary) 12%,transparent);color:var(--primary);font-size:20px;font-weight:800}.file-upload-copy{min-width:0;display:grid;gap:3px}.file-upload-copy strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.file-upload-copy small,.file-upload-hint{color:var(--text-muted,var(--muted));line-height:1.45}.file-upload-native{position:absolute!important;width:1px!important;height:1px!important;padding:0!important;margin:-1px!important;overflow:hidden!important;clip:rect(0,0,0,0)!important;white-space:nowrap!important;border:0!important}.file-upload-field.disabled{opacity:.58}.file-upload-box:focus-visible{outline:3px solid color-mix(in srgb,var(--primary) 28%,transparent);outline-offset:2px}
</style>
''')

write('frontend/src/views/AppDownloadView.vue', r'''<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api/client'

type AppVersion = {
  versionName: string
  versionCode: number
  minimumSupportedVersionCode: number
  downloadUrl: string
  sha256: string
  releaseNotes: string
  forceUpdate: boolean
  publishedAt: string
}

const institutionName = String(import.meta.env.VITE_INSTITUTION_NAME || '武汉科技大学')
const release = ref<AppVersion | null>(null)
const loading = ref(true)
const error = ref('')
const qrUrl = '/api/v1/mobile/app-version/download-qr'
const directUrl = computed(() => release.value?.downloadUrl || '')

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}
function shortSha(value: string) { return value ? `${value.slice(0, 12)}…${value.slice(-8)}` : '—' }

onMounted(async () => {
  try {
    const response = await api.get<AppVersion>('/api/v1/mobile/app-version')
    release.value = response.data
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '暂时无法获取 APP 版本信息'
  } finally { loading.value = false }
})
</script>

<template>
  <main class="download-page">
    <section class="download-shell">
      <header class="brand"><span class="brand-mark">WUST</span><div><strong>{{ institutionName }}</strong><small>统一宿舍管理平台</small></div></header>
      <div class="hero">
        <div class="hero-copy">
          <span class="eyebrow">ANDROID APP</span>
          <h1>宿舍管理 APP</h1>
          <p>手机端直接下载安装。电脑打开本页时，也可以使用手机扫描二维码立即下载最新已发布 APK。</p>
          <div v-if="loading" class="state">正在读取最新版本…</div>
          <div v-else-if="error" class="state error">{{ error }}</div>
          <template v-else-if="release">
            <div class="version-row"><strong>v{{ release.versionName }}</strong><span>构建号 {{ release.versionCode }}</span><span v-if="release.forceUpdate" class="force">重要更新</span></div>
            <a class="download-button" :href="directUrl" download>直接下载 APK</a>
            <p class="android-tip">仅支持 Android。若浏览器提示未知来源，请按系统提示允许本次安装。</p>
          </template>
        </div>
        <aside v-if="release" class="qr-card">
          <img :src="qrUrl" alt="扫描二维码直接下载 Android APK" />
          <strong>扫码直接下载</strong>
          <span>二维码内容就是 APK 下载地址，不经过第三方页面。</span>
        </aside>
      </div>
      <section v-if="release" class="release-card">
        <div><span>发布时间</span><strong>{{ formatDate(release.publishedAt) }}</strong></div>
        <div><span>最低支持构建号</span><strong>{{ release.minimumSupportedVersionCode }}</strong></div>
        <div><span>SHA-256</span><strong class="hash" :title="release.sha256">{{ shortSha(release.sha256) }}</strong></div>
        <article><span>更新说明</span><p>{{ release.releaseNotes || '本版本未填写额外更新说明。' }}</p></article>
      </section>
    </section>
  </main>
</template>

<style scoped>
.download-page{min-height:100vh;box-sizing:border-box;padding:28px;background:radial-gradient(circle at 85% 5%,rgba(37,99,235,.12),transparent 34%),#f5f8fc;color:#182337}.download-shell{width:min(980px,100%);margin:0 auto;display:grid;gap:22px}.brand{display:flex;align-items:center;gap:12px}.brand-mark{display:grid;place-items:center;width:48px;height:48px;border-radius:14px;background:#174a8b;color:#fff;font-weight:900;font-size:12px;letter-spacing:.08em}.brand>div{display:grid;gap:3px}.brand strong{font-size:16px}.brand small{color:#6d788a}.hero{display:grid;grid-template-columns:minmax(0,1fr) 260px;gap:28px;align-items:center;padding:42px;border:1px solid #dfe7f1;border-radius:26px;background:#fff;box-shadow:0 24px 60px rgba(26,57,94,.08)}.hero-copy{display:grid;justify-items:start;gap:14px}.eyebrow{color:#5270a0;font-size:12px;font-weight:800;letter-spacing:.14em}.hero h1{margin:0;font-size:clamp(34px,6vw,58px);line-height:1.05}.hero p{margin:0;max-width:640px;color:#667389;line-height:1.75}.version-row{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-top:4px}.version-row strong{font-size:20px}.version-row span{padding:5px 9px;border-radius:999px;background:#eef3f9;color:#526176;font-size:12px}.version-row .force{background:#fff0ed;color:#b43d2c}.download-button{display:inline-flex;align-items:center;justify-content:center;min-height:50px;padding:0 24px;border-radius:13px;background:#174a8b;color:#fff;font-weight:800;text-decoration:none;box-shadow:0 10px 22px rgba(23,74,139,.22)}.android-tip{font-size:12px!important}.state{padding:12px 14px;border-radius:12px;background:#f2f5f9;color:#68758a}.state.error{background:#fff0f0;color:#a12c2c}.qr-card{display:grid;justify-items:center;gap:9px;padding:18px;border:1px solid #e1e7ef;border-radius:18px;background:#f9fbfd;text-align:center}.qr-card img{width:196px;height:196px;border-radius:10px;background:#fff;image-rendering:pixelated}.qr-card span{color:#708096;font-size:12px;line-height:1.5}.release-card{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;padding:18px;border:1px solid #dfe7f1;border-radius:18px;background:#fff}.release-card>div,.release-card article{display:grid;gap:5px;padding:12px;border-radius:12px;background:#f7f9fc}.release-card span{color:#768398;font-size:12px}.release-card strong{overflow-wrap:anywhere}.release-card article{grid-column:1/-1}.release-card article p{margin:0;white-space:pre-wrap;line-height:1.65}.hash{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13px}@media(max-width:720px){.download-page{padding:16px}.hero{grid-template-columns:1fr;padding:26px 20px}.qr-card{display:none}.download-button{width:100%;box-sizing:border-box}.release-card{grid-template-columns:1fr}.release-card article{grid-column:auto}.brand{padding:4px}.hero h1{font-size:38px}}
</style>
''')

# Router: public /app that remains reachable when already authenticated.
replace_once('frontend/src/router/index.ts',
    "    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true, title: '登录' } },",
    "    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true, title: '登录' } },\n    { path: '/app', name: 'app-download', component: () => import('../views/AppDownloadView.vue'), meta: { public: true, allowAuthenticated: true, title: 'APP 下载' } },")
replace_once('frontend/src/router/index.ts',
    "  if (to.meta.public) {\n    if (auth.authenticated) {",
    "  if (to.meta.public) {\n    if (auth.authenticated && !to.meta.allowAuthenticated) {")

# Backend QR endpoint generated from the actual direct APK URL.
write('backend-java/server/src/main/java/com/wust/dormitory/mobile/MobileAppVersionController.java', r'''package com.wust.dormitory.mobile;

import com.wust.dormitory.label.print.pdf.PrintQrLayoutEngine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/mobile/app-version")
public class MobileAppVersionController {
    private static final int QR_SCALE = 8;
    private final MobileAppReleaseService releaseService;
    private final PrintQrLayoutEngine qrLayoutEngine;

    @Autowired
    public MobileAppVersionController(
            MobileAppReleaseService releaseService,
            PrintQrLayoutEngine qrLayoutEngine) {
        this.releaseService = releaseService;
        this.qrLayoutEngine = qrLayoutEngine;
    }

    MobileAppVersionController(
            String versionName,
            long versionCode,
            long minimumSupportedVersionCode,
            String downloadUrl,
            String sha256,
            String releaseNotes,
            boolean forceUpdate,
            String publishedAt) {
        var fallback = MobileAppReleaseService.ReleaseMetadata.validated(
                null, versionName, versionCode, minimumSupportedVersionCode,
                downloadUrl, sha256, releaseNotes, forceUpdate, publishedAt);
        this.releaseService = new MobileAppReleaseService(Optional::empty, fallback);
        this.qrLayoutEngine = new PrintQrLayoutEngine();
    }

    @GetMapping
    public ResponseEntity<AppVersionMetadata> getAppVersion() {
        var release = releaseService.currentRelease();
        return ResponseEntity.ok(new AppVersionMetadata(
                release.versionName(), release.versionCode(), release.minimumSupportedVersionCode(),
                release.downloadUrl(), release.sha256(), release.releaseNotes(), release.forceUpdate(), release.publishedAt()));
    }

    @GetMapping(value = "/download-qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getDownloadQr(HttpServletRequest request) {
        String downloadUrl = absoluteDownloadUrl(request, releaseService.currentRelease().downloadUrl());
        byte[] png = renderPng(qrLayoutEngine.matrix(downloadUrl));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    private String absoluteDownloadUrl(HttpServletRequest request, String downloadUrl) {
        URI uri = URI.create(downloadUrl);
        if (uri.isAbsolute()) return downloadUrl;
        String path = downloadUrl.startsWith("/") ? downloadUrl : "/" + downloadUrl;
        return ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(path).replaceQuery(null).build().toUriString();
    }

    private byte[] renderPng(PrintQrLayoutEngine.QrMatrix matrix) {
        int pixels = matrix.size() * QR_SCALE;
        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < matrix.size(); y++) {
            for (int x = 0; x < matrix.size(); x++) {
                int rgb = matrix.dark(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB();
                for (int py = y * QR_SCALE; py < (y + 1) * QR_SCALE; py++)
                    for (int px = x * QR_SCALE; px < (x + 1) * QR_SCALE; px++) image.setRGB(px, py, rgb);
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("APP 下载二维码生成失败", exception);
        }
    }

    public record AppVersionMetadata(
            String versionName, long versionCode, long minimumSupportedVersionCode,
            String downloadUrl, String sha256, String releaseNotes, boolean forceUpdate, String publishedAt) { }
}
''')

# Welcome canonical token: only studentName maps to student.student_name.
replace_once('backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java',
    '        variables.put("studentName", text(student.get("student_name"), "同学"));\n        variables.put("学生姓名", text(student.get("student_name"), "同学"));',
    '        variables.put("studentName", text(student.get("student_name"), "同学"));')

# Welcome dashboard: shared switch, friendly token label and header save action.
add_import('frontend/src/views/admin/AdminDashboardView.vue', "import SystemSwitch from '../../components/form/SystemSwitch.vue'", "import WelcomeMessageEditor from '../../components/admin/WelcomeMessageEditor.vue'")
text = read('frontend/src/views/admin/AdminDashboardView.vue')
text = re.sub(r"const tokenExamples = \{[\s\S]*?\n\}", "const tokenExamples = [\n  { token: 'studentName', label: '学生姓名', example: '例如：张三' },\n  { token: '学号', label: '学号', example: '例如：202600000001' },\n  { token: '专业名称', label: '专业名称', example: '例如：软件工程' },\n  { token: '年级', label: '年级', example: '例如：2026级' },\n  { token: '培养层次', label: '培养层次', example: '例如：硕士生' },\n  { token: '国家或地区', label: '国家或地区', example: '例如：日本' },\n]", text, count=1)
# Remove the old lower save button before adding the header action.
text = re.sub(r'<button[^>]*@click="saveWelcomeSetting"[^>]*>[\s\S]*?</button>', '', text, count=1)
old_head = '<div class="section-head"><div><span class="eyebrow">首次登录欢迎</span><h3>新生欢迎语</h3><p>{{ multilingualWelcomeEnabled ? \'基础卡片按语言展示，其他国家或地区可单独配置；未配置时自动使用英语欢迎语。\' : \'当前系统权限仅开放中文欢迎语设置。\' }}</p></div></div>'
new_head = '<div class="section-head split-title"><div><span class="eyebrow">首次登录欢迎</span><h3>新生欢迎语</h3><p>{{ multilingualWelcomeEnabled ? \'基础卡片按语言展示，其他国家或地区可单独配置；未配置时自动使用英语欢迎语。\' : \'当前系统权限仅开放中文欢迎语设置。\' }}</p></div><button class="button primary" type="button" :disabled="welcomeSaving" @click="saveWelcomeSetting">{{ welcomeSaving ? \'正在保存…\' : \'保存全部欢迎语\' }}</button></div>'
if old_head not in text: raise RuntimeError('AdminDashboard: welcome header marker missing')
text = text.replace(old_head, new_head, 1)
text, n = re.subn(r'<label class="welcome-display-toggle"><input v-model="welcomeDisplayEnabled" type="checkbox" /><span><strong>是否显示欢迎语</strong><small>\{\{ welcomeDisplayEnabled \? \'学生首次登录时显示欢迎内容\' : \'暂不向学生展示欢迎内容\' \}\}</small></span></label>', '<SystemSwitch v-model="welcomeDisplayEnabled" label="是否显示欢迎语" :description="welcomeDisplayEnabled ? \'学生首次登录时显示欢迎内容\' : \'暂不向学生展示欢迎内容\'" />', text, count=1)
if n != 1: raise RuntimeError('AdminDashboard: welcome checkbox marker missing')
text, n = re.subn(r'<div class="welcome-token-toolbar"><span>插入学生信息</span><button v-for="\(example,name\) in tokenExamples" :key="name" type="button" :title="example" @mousedown\.prevent @click="insertWelcomeToken\(String\(name\)\)">\{\{ name \}\}</button></div>', '<div class="welcome-token-toolbar"><span>插入学生信息</span><button v-for="item in tokenExamples" :key="item.token" type="button" :title="item.example" @mousedown.prevent @click="insertWelcomeToken(item.token)">{{ item.label }}</button></div>', text, count=1)
if n != 1: raise RuntimeError('AdminDashboard: token toolbar marker missing')
write('frontend/src/views/admin/AdminDashboardView.vue', text)

# Preference policy switches.
add_import('frontend/src/views/admin/AdminPreferencePolicyView.vue', "import SystemSwitch from '../../components/form/SystemSwitch.vue'", "import type { DataObject, ObjectSuccessResponse } from '../../api/types'")
text = read('frontend/src/views/admin/AdminPreferencePolicyView.vue')
start = text.index('      <label class="policy-option">')
end = text.index('      <label class="form-stack">', start)
block = '''      <SystemSwitch v-model="policy.directPreferenceWithoutBatchAllowed" label="开放无批次直接设置个人偏好" description="只控制当前未被任何可参与批次纳入的学生。已被批次纳入的学生不受此开关阻断；此开关也不会允许未填写偏好的学生绕过选寝前置条件。默认开放。" />
      <SystemSwitch v-model="policy.allowWithoutQuestionnaire" label="允许未填写偏好直接选寝" :disabled="!policy.questionnaireBypassFeatureEnabled" :description="policy.questionnaireBypassFeatureEnabled ? '学校开关与系统管理员授权同时开启后才实际生效。' : '系统管理员当前未授权；当前实际不生效。'" />
      <SystemSwitch v-model="policy.allowStudentReselect" label="允许学生取消已有结果并重新选择" :disabled="!policy.studentReselectFeatureEnabled" :description="policy.studentReselectFeatureEnabled ? '学校开关与系统管理员授权同时开启，且选寝批次仍处于开放状态时才实际生效。' : '系统管理员当前未授权；当前实际不生效。'" />
      <SystemSwitch v-model="policy.allowStudentCancelManualAssignment" label="允许学生取消管理员指定床位" description="默认关闭。开启后仍需允许学生重新选择实际生效，且当前批次必须处于开放状态。" />
'''
text = text[:start] + block + text[end:]
write('frontend/src/views/admin/AdminPreferencePolicyView.vue', text)

# Matching recommendation and activation switches.
add_import('frontend/src/features/admin-matching/components/AdminMatchingPage.vue', "import SystemSwitch from '../../../components/form/SystemSwitch.vue'", "import { useAdminMatchingView } from '../composables/useAdminMatchingView'")
text = read('frontend/src/features/admin-matching/components/AdminMatchingPage.vue')
pattern = r'<div class="recommendation-policy-grid">[\s\S]*?</div>\n          <div class="form-grid three-column recommendation-parameters">'
replacement = '''<div class="recommendation-policy-grid">
            <SystemSwitch v-for="definition in recommendationDefinitions" :key="definition.value" :model-value="recommendationPolicy.allowed.includes(definition.value)" :label="definition.label" :description="definition.description" @update:model-value="toggleRecommendationStrategy(definition.value, $event)" />
          </div>
          <div class="form-grid three-column recommendation-parameters">'''
text, n = re.subn(pattern, replacement, text, count=1)
if n != 1: raise RuntimeError('AdminMatchingPage: recommendation grid marker missing')
text, n = re.subn(r'<label class="checkbox-line matching-activate"><input v-model="form\.activate" type="checkbox" />保存后设为新批次默认使用的启用修订</label>', '<SystemSwitch v-model="form.activate" label="保存后设为新批次默认使用的启用修订" />', text, count=1)
if n != 1: raise RuntimeError('AdminMatchingPage: activate checkbox marker missing')
write('frontend/src/features/admin-matching/components/AdminMatchingPage.vue', text)

# Inspection switches: replace each checkbox label containing the two policy fields.
add_import('frontend/src/views/admin/AdminInspectionView.vue', "import SystemSwitch from '../../components/form/SystemSwitch.vue'", "import PaginationBar from '../../components/common/PaginationBar.vue'")
text = read('frontend/src/views/admin/AdminInspectionView.vue')
for model, label, desc in [
    ('taskForm.requireRoomQr','要求扫码确认寝室','开启后，宿管进入寝室执行查寝前必须扫描当前寝室二维码。'),
    ('taskForm.allowManualRoomJump','允许手工切换寝室','开启后，宿管可在移动端手工切换到任务范围内的其他寝室。'),
    ('scheduleForm.requireRoomQr','要求扫码确认寝室','开启后，由周期计划生成的查寝执行同样要求扫描寝室二维码。'),
    ('scheduleForm.allowManualRoomJump','允许手工切换寝室','开启后，由周期计划生成的执行允许手工切换任务范围内寝室。'),
]:
    pat = rf'<label[^>]*>\s*<input[^>]*v-model="{re.escape(model)}"[^>]*type="checkbox"[^>]*/?>[\s\S]*?</label>'
    repl = f'<SystemSwitch v-model="{model}" label="{label}" description="{desc}" />'
    text, n = re.subn(pat, repl, text, count=1)
    if n != 1: raise RuntimeError(f'AdminInspectionView: switch marker missing {model}')
write('frontend/src/views/admin/AdminInspectionView.vue', text)

# Waitlist switch.
add_import('frontend/src/views/admin/AdminWaitlistView.vue', "import SystemSwitch from '../../components/form/SystemSwitch.vue'", "import PaginationBar from '../../components/common/PaginationBar.vue'")
replace_once('frontend/src/views/admin/AdminWaitlistView.vue',
    '<label class="toggle-field"><input v-model="policyForm.enabled" type="checkbox"><span>开放学生候补补位</span></label>',
    '<SystemSwitch v-model="policyForm.enabled" label="开放学生候补补位" description="关闭后不会新增候补或自动发出邀请，已有历史仍可查询。" />')

# File upload component imports and replacements.
add_import('frontend/src/views/platform/PlatformSiteMetadataView.vue', "import FileUploadField from '../../components/form/FileUploadField.vue'", "import { applySchoolTheme } from '../../site/theme'")
text = read('frontend/src/views/platform/PlatformSiteMetadataView.vue')
for slot in ('SQUARE_LOGO','HORIZONTAL_LOGO','LOGIN_IMAGE'):
    pat = rf'<input type="file" accept="image/png,image/jpeg,image/webp" :disabled="Boolean\(uploadingSlot\)" @change="uploadAsset\(\'{slot}\', \$event\)" />'
    repl = f'<FileUploadField accept="image/png,image/jpeg,image/webp" :disabled="Boolean(uploadingSlot)" button-label="选择图片" @change="uploadAsset(\'{slot}\', $event)" />'
    text, n = re.subn(pat, repl, text, count=1)
    if n != 1: raise RuntimeError(f'PlatformSiteMetadataView: upload marker missing {slot}')
write('frontend/src/views/platform/PlatformSiteMetadataView.vue', text)

add_import('frontend/src/views/admin/AdminSiteSettingsView.vue', "import FileUploadField from '../../components/form/FileUploadField.vue'", "import { applySchoolTheme } from '../../site/theme'")
replace_once('frontend/src/views/admin/AdminSiteSettingsView.vue',
    '<input class="input" type="file" accept="image/png,image/jpeg,image/webp" :disabled="!editable || imageUploading" @change="uploadLoginImage" />',
    '<FileUploadField accept="image/png,image/jpeg,image/webp" :disabled="!editable || imageUploading" button-label="选择登录页图片" @change="uploadLoginImage" />')

add_import('frontend/src/components/admin/ImportWorkflowModal.vue', "import FileUploadField from '../form/FileUploadField.vue'", "import { useI18n } from '../../i18n'")
text = read('frontend/src/components/admin/ImportWorkflowModal.vue')
text = text.replace("const fileInput = ref<HTMLInputElement | null>(null)\n", '')
text = text.replace("  if (fileInput.value) fileInput.value.value = ''\n", '')
text, n = re.subn(r'<input ref="fileInput" class="input" type="file" accept="\.csv,\.xls,\.xlsx" required @change="chooseFile" />', '<FileUploadField accept=".csv,.xls,.xlsx" :file-name="file?.name || \'\'" button-label="选择 Excel 或 CSV 文件" @change="chooseFile" />', text, count=1)
if n != 1: raise RuntimeError('ImportWorkflowModal: file marker missing')
write('frontend/src/components/admin/ImportWorkflowModal.vue', text)

add_import('frontend/src/views/admin/AdminImportQualityView.vue', "import FileUploadField from '../../components/form/FileUploadField.vue'", "import { AppConfirmDialog } from '../../components/modal'")
replace_once('frontend/src/views/admin/AdminImportQualityView.vue',
    '<input class="input" type="file" accept=".csv,.xls,.xlsx" required @change="chooseFile" />',
    '<FileUploadField accept=".csv,.xls,.xlsx" :file-name="file?.name || \'\'" button-label="选择导入文件" @change="chooseFile" />')

add_import('frontend/src/views/platform/PlatformAppVersionsView.vue', "import FileUploadField from '../../components/form/FileUploadField.vue'", "import { platformAppVersionsApi, type AppRelease, type AppReleaseStatus } from '../../platform/appVersions'")
add_import('frontend/src/views/platform/PlatformAppVersionsView.vue', "import SystemSwitch from '../../components/form/SystemSwitch.vue'", "import FileUploadField from '../../components/form/FileUploadField.vue'")
text = read('frontend/src/views/platform/PlatformAppVersionsView.vue')
text, n = re.subn(r'<label class="file-picker"><input type="file" accept="\.apk,application/vnd\.android\.package-archive" @change="onFileChange" /><span>\{\{ uploadFile\?\.name \|\| \'选择 APK 文件\' \}\}</span></label>', '<FileUploadField accept=".apk,application/vnd.android.package-archive" :file-name="uploadFile?.name || \'\'" button-label="选择 APK 文件" hint="仅支持 Android APK，服务器会校验包名、版本与完整性。" @change="onFileChange" />', text, count=1)
if n != 1: raise RuntimeError('PlatformAppVersionsView: APK picker marker missing')
text, n = re.subn(r'<label class="force-check"><input v-model="editForce" type="checkbox" /><span>强制所有低于当前构建号的客户端升级到此版本</span></label>', '<SystemSwitch v-model="editForce" label="强制所有低于当前构建号的客户端升级到此版本" />', text, count=1)
if n != 1: raise RuntimeError('PlatformAppVersionsView: force checkbox marker missing')
write('frontend/src/views/platform/PlatformAppVersionsView.vue', text)

# Print template uploader: FileUploadField exposes open()/clear(), preserving programmatic picker flows.
add_import('frontend/src/views/admin/AdminMobilePrintTemplateView.vue', "import FileUploadField from '../../components/form/FileUploadField.vue'", "import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'")
text = read('frontend/src/views/admin/AdminMobilePrintTemplateView.vue')
text = text.replace('const assetInput = ref<HTMLInputElement | null>(null)', "const assetInput = ref<InstanceType<typeof FileUploadField> | null>(null)")
text = text.replace('const imageInput = ref<HTMLInputElement | null>(null)', "const imageInput = ref<InstanceType<typeof FileUploadField> | null>(null)")
text = text.replace('assetInput.value?.click()', 'assetInput.value?.open()').replace('imageInput.value?.click()', 'imageInput.value?.open()')
text = text.replace("assetInput.value.value = ''", 'assetInput.value.clear()').replace("imageInput.value.value = ''", 'imageInput.value.clear()')
# Transform any remaining raw file inputs while retaining ref/accept/disabled/change attributes supported by the component.
def convert_print_input(match):
    tag = match.group(0)
    refm = re.search(r'ref="([^"]+)"', tag); acceptm = re.search(r'accept="([^"]+)"', tag); changem = re.search(r'@change="([^"]+)"', tag); dism = re.search(r'(:disabled="[^"]+")', tag)
    attrs = []
    if refm: attrs.append(f'ref="{refm.group(1)}"')
    if acceptm: attrs.append(f'accept="{acceptm.group(1)}"')
    if dism: attrs.append(dism.group(1))
    if changem: attrs.append(f'@change="{changem.group(1)}"')
    attrs.append('button-label="选择图片文件"')
    return '<FileUploadField ' + ' '.join(attrs) + ' />'
text, n = re.subn(r'<input\b(?=[^>]*type="file")[^>]*?/?>', convert_print_input, text)
if n < 1: raise RuntimeError('AdminMobilePrintTemplateView: no raw file input found')
write('frontend/src/views/admin/AdminMobilePrintTemplateView.vue', text)

# Import settings booleans also use the same shared switch language.
add_import('frontend/src/components/admin/ImportWorkflowModal.vue', "import SystemSwitch from '../form/SystemSwitch.vue'", "import FileUploadField from '../form/FileUploadField.vue'")
text = read('frontend/src/components/admin/ImportWorkflowModal.vue')
text, n = re.subn(r'<label class="policy-switch"><input v-model="roomAutoCreateBuilding" type="checkbox" /><span>\{\{ roomAutoCreateBuilding \? \'允许自动创建\' : \'必须提前创建\' \}\}</span></label>', '<SystemSwitch v-model="roomAutoCreateBuilding" label="允许自动创建不存在的楼栋" />', text, count=1)
if n != 1: raise RuntimeError('ImportWorkflowModal: policy checkbox marker missing')
write('frontend/src/components/admin/ImportWorkflowModal.vue', text)

add_import('frontend/src/views/admin/AdminImportQualityView.vue', "import SystemSwitch from '../../components/form/SystemSwitch.vue'", "import FileUploadField from '../../components/form/FileUploadField.vue'")
text = read('frontend/src/views/admin/AdminImportQualityView.vue')
text, n = re.subn(r'<label class="policy-toggle">\s*<input v-model="importSettings\.roomAutoCreateBuilding" type="checkbox" />[\s\S]*?</label>', '<SystemSwitch v-model="importSettings.roomAutoCreateBuilding" label="允许宿舍导入自动创建不存在的楼栋" description="关闭时未知楼栋会在预检中报错；开启后仅在正式提交时创建，预检不会写正式业务数据。" />', text, count=1)
if n != 1: raise RuntimeError('AdminImportQualityView: policy checkbox marker missing')
write('frontend/src/views/admin/AdminImportQualityView.vue', text)

# Object history: business label is captured at selection time; IDs remain transport-only.
text = read('frontend/src/features/admin-governance/components/ObjectHistoryWorkspace.vue')
text = text.replace("const activeId = ref(0)\n", "const activeId = ref(0)\nconst activeLabel = ref('')\n")
text = text.replace("  activeId.value = 0\n  if (subjectType.value", "  activeId.value = 0\n  activeLabel.value = ''\n  if (subjectType.value", 1)
marker = "const selectedId = computed(() => {\n  if (subjectType.value === 'STUDENT') return selectedStudentId.value\n  if (subjectType.value === 'ROOM') return selectedRoomId.value\n  return selectedBedId.value\n})"
selected = marker + "\nconst selectedLabel = computed(() => {\n  if (subjectType.value === 'STUDENT') {\n    const item = studentOptions.value.find(row => Number(row.id) === selectedStudentId.value)\n    return item ? `${item.student_number} · ${item.student_name}` : ''\n  }\n  const room = roomOptions.value.find(row => Number(row.id) === selectedRoomId.value)\n  if (!room) return ''\n  const roomLabel = [room.campus_name, room.building_name, `${room.room_number}室`].filter(Boolean).join(' / ')\n  if (subjectType.value === 'ROOM') return roomLabel\n  const bed = bedOptions.value.find(row => Number(row.id) === selectedBedId.value)\n  return bed ? `${roomLabel} / ${bed.bed_code}` : ''\n})"
if marker not in text: raise RuntimeError('ObjectHistoryWorkspace: selectedId marker missing')
text = text.replace(marker, selected, 1)
text = text.replace("    activeId.value = 0\n    return", "    activeId.value = 0\n    activeLabel.value = ''\n    return", 1)
text = text.replace("  activeType.value = subjectType.value\n  activeId.value = id", "  activeType.value = subjectType.value\n  activeId.value = id\n  activeLabel.value = selectedLabel.value || subjectName(subjectType.value)")
text = text.replace(':title="`${subjectName(activeType)} #${activeId} 操作历史`"', ':title="`${activeLabel} 操作历史`"')
write('frontend/src/features/admin-governance/components/ObjectHistoryWorkspace.vue', text)

text = read('frontend/src/components/ObjectAuditTimeline.vue')
text = text.replace("function operatorLabel(item: ObjectTimelineItem) {", "function subjectTypeLabel(value: string) {\n  return ({ STUDENT: '学生', ROOM: '寝室', BED: '床位' } as Record<string, string>)[value] ?? '业务对象'\n}\n\nfunction operatorLabel(item: ObjectTimelineItem) {")
text = text.replace("{{ subject.relation }} · {{ subject.label || `${subject.type} #${subject.id}` }}", "{{ subject.relation }} · {{ subject.label || subjectTypeLabel(subject.type) }}")
write('frontend/src/components/ObjectAuditTimeline.vue', text)

print('app download and UI unification patch applied')
