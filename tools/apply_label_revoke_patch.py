from pathlib import Path
import json

root = Path('private-repo')

def replace(path: str, old: str, new: str):
    target = root / path
    text = target.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'expected block not found in {path}')
    target.write_text(text.replace(old, new, 1), encoding='utf-8')

# 1. Backend: allow single-code permanent revoke for both UNBOUND and BOUND labels.
service = 'backend-java/server/src/main/java/com/wust/dormitory/label/ResourceLabelService.java'
old_revoke = '''    @Transactional
    public Map<String, Object> revoke(CurrentUser user, String rawCode, String reason) {
        AdminAuthorizationContext context = authorizationService.requireBusinessPermission(user, "label.rebind");
        String code = normalizeCode(rawCode);
        Map<String, Object> before = requireLabel(code);
        if (!"BOUND".equals(text(before.get("label_state")))) {
            throw new BusinessException("RESOURCE_LABEL_NOT_BOUND", "只有已绑定标签可以撤销", HttpStatus.CONFLICT);
        }
        String resourceType = normalizeResourceType(text(before.get("resource_type")));
        long resourceId = number(before.get("resource_id"));
        Map<String, Object> target = requireTarget(resourceType, resourceId);
        requireTargetScope(context, target, AdminScopeAccess.WRITE);
        String normalizedReason = requiredText(reason, "请填写撤销标签原因");
        if (mapper.revokeBoundLabel(code, user.userId(), normalizedReason) != 1) {
            throw new BusinessException("RESOURCE_LABEL_REVOKE_CONFLICT", "标签状态已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        Map<String, Object> after = requireLabel(code);
        insertAudit(after, "REVOKE", resourceType, resourceId, null, null, user.userId(), normalizedReason);
        return Map.of(
                "publicCode", code,
                "state", "REVOKED",
                "targetLabel", String.valueOf(target.get("target_label")),
                "message", "旧标签已永久停用；如需更换，请生成或绑定新的8位标签");
    }
'''
new_revoke = '''    @Transactional
    public Map<String, Object> revoke(CurrentUser user, String rawCode, String reason) {
        AdminAuthorizationContext context = authorizationService.requireBusinessPermission(user, "label.rebind");
        String code = normalizeCode(rawCode);
        Map<String, Object> before = requireLabel(code);
        String state = text(before.get("label_state"));
        if ("REVOKED".equals(state)) {
            throw new BusinessException("RESOURCE_LABEL_ALREADY_REVOKED", "该标签已经作废，无需重复操作", HttpStatus.CONFLICT);
        }
        if (!"UNBOUND".equals(state) && !"BOUND".equals(state)) {
            throw new BusinessException("RESOURCE_LABEL_REVOKE_STATE_INVALID", "当前标签状态不允许作废", HttpStatus.CONFLICT);
        }

        String resourceType = null;
        Long resourceId = null;
        Map<String, Object> target = null;
        if ("BOUND".equals(state)) {
            resourceType = normalizeResourceType(text(before.get("resource_type")));
            resourceId = number(before.get("resource_id"));
            target = requireTarget(resourceType, resourceId);
            requireTargetScope(context, target, AdminScopeAccess.WRITE);
        }

        String normalizedReason = requiredText(reason, "请填写作废标签原因");
        if (mapper.revokeLabel(code, user.userId(), normalizedReason) != 1) {
            throw new BusinessException("RESOURCE_LABEL_REVOKE_CONFLICT", "标签状态已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        Map<String, Object> after = requireLabel(code);
        insertAudit(after, "REVOKE", resourceType, resourceId, null, null, user.userId(), normalizedReason);
        long batchId = number(before.get("batch_id"));
        if (batchId > 0) mapper.refreshBatchStatus(batchId);
        return Map.of(
                "publicCode", code,
                "state", "REVOKED",
                "targetLabel", target == null ? "未绑定标签" : String.valueOf(target.get("target_label")),
                "message", "标签已永久作废；如需继续使用对应资源，请生成或绑定新的8位标签");
    }
'''
replace(service, old_revoke, new_revoke)

# 2. Frontend API: single-label revoke by public code.
labels_api = root / 'app/src/api/labels.ts'
labels_text = labels_api.read_text(encoding='utf-8')
marker = '''export async function bindResourceLabel(code: string, resourceType: ResourceLabelType, resourceId: number): Promise<ResourceLabelResolution> {
  const response = await api.post<ApiEnvelope<ResourceLabelResolution>>(`/api/v1/mobile/labels/${encodeURIComponent(code)}/bind`, { resourceType, resourceId })
  return response.data.data
}
'''
addition = marker + '''\nexport async function revokeResourceLabel(code: string, reason: string): Promise<ResourceLabelResolution> {
  const response = await api.post<ApiEnvelope<ResourceLabelResolution>>(`/api/v1/mobile/labels/${encodeURIComponent(code)}/revoke`, { reason })
  return response.data.data
}
'''
if marker not in labels_text:
    raise SystemExit('labels API bind marker not found')
labels_api.write_text(labels_text.replace(marker, addition, 1), encoding='utf-8')

# 3. Label manager: expose dedicated revoke tab.
(root / 'app/src/components/ResourceLabelPrintPanel.vue').write_text('''<script setup lang="ts">
import { computed, ref } from 'vue'
import ResourceLabelBatchHistory from './ResourceLabelBatchHistory.vue'
import ResourceLabelCreatePrintPanel from './ResourceLabelCreatePrintPanel.vue'
import ResourceLabelQuickRevokePanel from './ResourceLabelQuickRevokePanel.vue'
import { useAuthStore } from '../stores/auth'

type BatchTab = 'CREATE' | 'HISTORY' | 'REVOKE'
const auth = useAuthStore()
const canGenerate = computed(() => auth.can('P3_RESOURCE_LABEL_GENERATE', 'label.generate'))
const canPrint = computed(() => auth.can('P3_RESOURCE_LABEL_PRINT', 'label.print'))
const canRebind = computed(() => auth.can('P3_RESOURCE_LABEL_REBIND', 'label.rebind'))
const tab = ref<BatchTab>(canGenerate.value ? 'CREATE' : 'HISTORY')
</script>

<template>
  <div class="label-batch-manager stack">
    <div class="manager-tabs">
      <button v-if="canGenerate" type="button" :class="{ active: tab === 'CREATE' }" @click="tab='CREATE'">新建标签</button>
      <button type="button" :class="{ active: tab === 'HISTORY' }" @click="tab='HISTORY'">历史批次</button>
      <button v-if="canRebind" type="button" :class="{ active: tab === 'REVOKE' }" @click="tab='REVOKE'">作废标签</button>
    </div>
    <ResourceLabelCreatePrintPanel v-if="tab === 'CREATE' && canGenerate" :can-print="canPrint" />
    <ResourceLabelBatchHistory v-else-if="tab === 'HISTORY'" :can-print="canPrint" :can-revoke="canRebind" />
    <ResourceLabelQuickRevokePanel v-else-if="tab === 'REVOKE' && canRebind" :scan-route="{ path: '/scan', query: { intent: 'revoke' } }" />
  </div>
</template>
<style scoped>
.label-batch-manager{display:grid;gap:10px}.manager-tabs{display:grid;grid-template-columns:repeat(auto-fit,minmax(0,1fr));gap:6px;padding:4px;border-radius:14px;background:var(--soft)}.manager-tabs button{border:0;border-radius:10px;padding:10px;background:transparent;color:var(--muted);font-weight:800}.manager-tabs button.active{background:#fff;color:var(--primary);box-shadow:0 2px 10px rgba(20,46,90,.08)}
</style>
''', encoding='utf-8')

# 4. Dedicated quick-revoke panel: number lookup and scan entry share the same confirmation flow.
(root / 'app/src/components/ResourceLabelQuickRevokePanel.vue').write_text('''<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { resolveResourceLabel, revokeResourceLabel, type ResourceLabelResolution } from '../api/labels'
import { useMobileNotice } from '../platform/mobile-notice'
import { parseResourceLabelCode } from '../platform/scanner'
import ResourceLabelRevokeDialog from './ResourceLabelRevokeDialog.vue'

const props = defineProps<{ scanRoute: RouteLocationRaw }>()
const router = useRouter()
const notice = useMobileNotice()
const code = ref('')
const resolving = ref(false)
const revoking = ref(false)
const pendingLabel = ref<ResourceLabelResolution | null>(null)

const pendingDescription = computed(() => {
  const label = pendingLabel.value
  if (!label) return '作废后不可恢复。'
  const state = label.state === 'BOUND' ? '已绑定' : '未绑定'
  return `标签 ${label.publicCode} · ${state} · ${label.targetLabel}。作废后不可恢复，请确认编号和资源无误。`
})

async function lookupByCode() {
  const publicCode = parseResourceLabelCode(code.value)
  if (!publicCode) {
    notice.error('请输入8位数字标签编号。')
    return
  }
  if (resolving.value) return
  resolving.value = true
  try {
    pendingLabel.value = await resolveResourceLabel(publicCode)
    code.value = publicCode
  } catch (reason) {
    notice.error(reason instanceof Error ? reason.message : '标签查询失败')
  } finally {
    resolving.value = false
  }
}

async function confirmRevoke(reason: string) {
  const publicCode = pendingLabel.value?.publicCode
  if (!publicCode || revoking.value) return
  revoking.value = true
  try {
    const result = await revokeResourceLabel(publicCode, reason)
    pendingLabel.value = null
    code.value = ''
    notice.success(`标签 ${publicCode} 已永久作废${result.targetLabel ? ` · ${result.targetLabel}` : ''}`)
  } catch (cause) {
    notice.error(cause instanceof Error ? cause.message : '标签作废失败')
  } finally {
    revoking.value = false
  }
}

function closeDialog() {
  if (revoking.value) return
  pendingLabel.value = null
}
</script>

<template>
  <section class="quick-revoke panel stack">
    <div class="section-title">
      <div><h3>选择作废方式</h3><p>支持直接输入8位标签编号，或打开扫码器读取二维码；提交前都会再次核对标签。</p></div>
    </div>

    <div class="revoke-method-grid">
      <article class="method-card">
        <div class="method-copy"><strong>按编号作废</strong><span>适合标签损坏但编号仍可辨认，或根据台账直接处理。</span></div>
        <form class="code-form" @submit.prevent="lookupByCode">
          <input v-model.trim="code" class="input code-input" inputmode="numeric" maxlength="8" placeholder="输入8位标签编号" autocomplete="off" />
          <button class="button danger" type="submit" :disabled="resolving">{{ resolving ? '核对中…' : '核对并作废' }}</button>
        </form>
      </article>

      <button class="method-card scan-method" type="button" @click="router.push(props.scanRoute)">
        <span class="scan-icon">⌗</span>
        <span class="method-copy"><strong>扫码作废</strong><span>打开扫码器，对准二维码后自动读取编号并进入作废确认。</span></span>
        <span class="method-arrow">›</span>
      </button>
    </div>

    <div class="danger-tip"><strong>注意</strong><span>作废是永久操作。已绑定标签作废后，原二维码将无法继续进入资源；未绑定的预印标签也会永久失效。</span></div>
  </section>

  <ResourceLabelRevokeDialog
    :open="Boolean(pendingLabel)"
    :title="pendingLabel?.publicCode ? `作废标签 ${pendingLabel.publicCode}` : '作废标签'"
    :description="pendingDescription"
    :busy="revoking"
    @close="closeDialog"
    @confirm="confirmRevoke"
  />
</template>

<style scoped>
.quick-revoke{gap:14px}.revoke-method-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.method-card{min-width:0;display:grid;gap:12px;padding:14px;border:1px solid var(--line);border-radius:14px;background:var(--panel);text-align:left}.method-copy{display:grid;gap:5px}.method-copy strong{font-size:14px;color:var(--ink)}.method-copy span{font-size:11px;line-height:1.55;color:var(--muted)}.code-form{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px}.code-input{font-variant-numeric:tabular-nums;letter-spacing:.08em}.scan-method{grid-template-columns:auto minmax(0,1fr) auto;align-items:center;cursor:pointer;color:inherit}.scan-icon{display:grid;place-items:center;width:38px;height:38px;border-radius:11px;background:var(--soft);font-size:22px;font-weight:800;color:var(--primary)}.method-arrow{font-size:24px;color:var(--muted)}.danger-tip{display:flex;gap:8px;padding:10px 12px;border-radius:11px;background:#fff2f0;color:#8f1d18}.danger-tip span{font-size:11px;line-height:1.55;color:#a33a34}.button.danger{border-color:#c9342d;background:#c9342d;color:#fff}@media(max-width:620px){.revoke-method-grid{grid-template-columns:1fr}.code-form{grid-template-columns:1fr}.code-form .button{width:100%}}
</style>
''', encoding='utf-8')

# 5. ScanView: intent=revoke reuses the existing fast native/web scanner, but routes scan results to revoke confirmation.
scan = 'app/src/views/ScanView.vue'
replace(scan, "import { useRouter } from 'vue-router'", "import { useRoute, useRouter } from 'vue-router'")
replace(scan, '''  bindResourceLabel,\n  resolveResourceLabel,''', '''  bindResourceLabel,\n  resolveResourceLabel,\n  revokeResourceLabel,''')
replace(scan, "import ResourceLabelBindSelector from '../components/ResourceLabelBindSelector.vue'", "import ResourceLabelBindSelector from '../components/ResourceLabelBindSelector.vue'\nimport ResourceLabelRevokeDialog from '../components/ResourceLabelRevokeDialog.vue'")
replace(scan, '''const router = useRouter()\nconst auth = useAuthStore()''', '''const route = useRoute()\nconst router = useRouter()\nconst auth = useAuthStore()''')
replace(scan, '''const canBind = computed(() => auth.can('P3_RESOURCE_LABEL_BIND', 'label.bind'))\nconst manualCode = ref('')''', '''const canBind = computed(() => auth.can('P3_RESOURCE_LABEL_BIND', 'label.bind'))\nconst canRevoke = computed(() => auth.can('P3_RESOURCE_LABEL_REBIND', 'label.rebind'))\nconst revokeIntent = computed(() => route.query.intent === 'revoke')\nconst manualCode = ref('')''')
replace(scan, '''const pendingLabel = ref<ResourceLabelResolution | null>(null)\nconst bindType''', '''const pendingLabel = ref<ResourceLabelResolution | null>(null)\nconst pendingRevokeLabel = ref<ResourceLabelResolution | null>(null)\nconst bindType''')
replace(scan, '''const binding = ref(false)''', '''const binding = ref(false)\nconst revoking = ref(false)''')
replace(scan, '''  try {\n    const publicCode = parseResourceLabelCode(raw)\n    if (publicCode) {''', '''  try {\n    const publicCode = parseResourceLabelCode(raw)\n    if (revokeIntent.value) {\n      if (!canRevoke.value) throw new Error('当前岗位没有标签作废权限')\n      if (!publicCode) throw new Error('作废标签仅支持8位数字标签编号')\n      const result = await resolveResourceLabel(publicCode)\n      await stopCamera()\n      pendingRevokeLabel.value = result\n      manualCode.value = publicCode\n      return\n    }\n    if (publicCode) {''')
replace(scan, '''function cancelBinding() { pendingLabel.value = null; bindTarget.value = null; error.value = ''; void startCamera() }''', '''function cancelBinding() { pendingLabel.value = null; bindTarget.value = null; error.value = ''; void startCamera() }\n\nasync function revokePending(reason: string) {\n  const code = pendingRevokeLabel.value?.publicCode\n  if (!code || revoking.value) return\n  revoking.value = true\n  try {\n    const result = await revokeResourceLabel(code, reason)\n    pendingRevokeLabel.value = null\n    manualCode.value = ''\n    notice.success(`标签 ${code} 已永久作废${result.targetLabel ? ` · ${result.targetLabel}` : ''}`)\n    await nextTick()\n    await startCamera()\n  } catch (cause) {\n    notice.error(cause instanceof Error ? cause.message : '标签作废失败')\n  } finally { revoking.value = false }\n}\n\nfunction cancelRevoke() {\n  if (revoking.value) return\n  pendingRevokeLabel.value = null\n  manualCode.value = ''\n  error.value = ''\n  void startCamera()\n}''')
replace(scan, '''async function startCamera() {\n  if (pendingLabel.value || cameraActive.value) return\n  error.value = ''\n  if (nativeAndroid) {''', '''async function startCamera() {\n  if (pendingLabel.value || pendingRevokeLabel.value || cameraActive.value) return\n  error.value = ''\n  if (revokeIntent.value && !canRevoke.value) { error.value = '当前岗位没有标签作废权限'; return }\n  if (nativeAndroid) {''')
replace(scan, '''  if (!pendingLabel.value) { await nextTick(); await startCamera() }''', '''  if (!pendingLabel.value && !pendingRevokeLabel.value) { await nextTick(); await startCamera() }''')
replace(scan, '''      <section v-if="nativeAndroid && !pendingLabel" class="panel native-scan-actions"><div class="native-scan-copy"><strong>扫描资源标签</strong><span>打开原生扫码器扫描二维码，或直接输入标签编码。</span></div><button class="button primary full" type="button" :disabled="cameraActive" @click="startCamera">{{ cameraActive ? '正在打开扫码器…' : '继续扫码' }}</button></section>\n      <section v-if="!nativeAndroid && !pendingLabel" class="scanner-panel embedded-panel"><div class="scanner-preview-slot"><video ref="video" class="scanner-video" playsinline muted></video><div v-if="!cameraActive" class="scanner-placeholder"><strong>二维码扫描</strong><span>正在准备摄像头，可同时手动输入8位编码</span></div></div><div class="scanner-controls"><button v-if="cameraActive && torchSupported" class="button secondary compact" type="button" @click="toggleTorch">{{ torchActive ? '关闭闪光灯' : '闪光灯' }}</button><button v-if="!cameraActive" class="button primary compact" type="button" @click="startCamera">重新启动摄像头</button></div></section>\n      <section v-if="!pendingLabel" class="panel manual-entry-card"><div class="manual-divider"><span>或手动输入</span></div><form class="search-row" @submit.prevent="submitCode()"><input v-model.trim="manualCode" class="input code-input" inputmode="text" maxlength="64" placeholder="输入8位标签编码" autocomplete="off" /><button class="button secondary" type="submit">识别</button></form></section>''', '''      <section v-if="nativeAndroid && !pendingLabel && !pendingRevokeLabel" class="panel native-scan-actions"><div class="native-scan-copy"><strong>{{ revokeIntent ? '扫描待作废标签' : '扫描资源标签' }}</strong><span>{{ revokeIntent ? '扫描二维码后先核对标签信息，再选择原因并永久作废。' : '打开原生扫码器扫描二维码，或直接输入标签编码。' }}</span></div><button class="button primary full" type="button" :disabled="cameraActive" @click="startCamera">{{ cameraActive ? '正在打开扫码器…' : '继续扫码' }}</button></section>\n      <section v-if="!nativeAndroid && !pendingLabel && !pendingRevokeLabel" class="scanner-panel embedded-panel"><div class="scanner-preview-slot"><video ref="video" class="scanner-video" playsinline muted></video><div v-if="!cameraActive" class="scanner-placeholder"><strong>{{ revokeIntent ? '扫描待作废二维码' : '二维码扫描' }}</strong><span>正在准备摄像头，可同时手动输入8位编码</span></div></div><div class="scanner-controls"><button v-if="cameraActive && torchSupported" class="button secondary compact" type="button" @click="toggleTorch">{{ torchActive ? '关闭闪光灯' : '闪光灯' }}</button><button v-if="!cameraActive" class="button primary compact" type="button" @click="startCamera">重新启动摄像头</button></div></section>\n      <section v-if="!pendingLabel && !pendingRevokeLabel" class="panel manual-entry-card"><div class="manual-divider"><span>{{ revokeIntent ? '或按编号作废' : '或手动输入' }}</span></div><form class="search-row" @submit.prevent="submitCode()"><input v-model.trim="manualCode" class="input code-input" :inputmode="revokeIntent ? 'numeric' : 'text'" :maxlength="revokeIntent ? 8 : 64" placeholder="输入8位标签编码" autocomplete="off" /><button class="button secondary" type="submit">{{ revokeIntent ? '核对' : '识别' }}</button></form></section>''')
replace(scan, '''      </section>\n    </div>\n  </main>\n</template>''', '''      </section>\n    </div>\n\n    <ResourceLabelRevokeDialog\n      :open="Boolean(pendingRevokeLabel)"\n      :title="pendingRevokeLabel?.publicCode ? `作废标签 ${pendingRevokeLabel.publicCode}` : '作废标签'"\n      :description="pendingRevokeLabel ? `当前标签：${pendingRevokeLabel.targetLabel}。作废后不可恢复，请确认扫描结果无误。` : '作废后不可恢复。'"\n      :busy="revoking"\n      @close="cancelRevoke"\n      @confirm="revokePending"\n    />\n  </main>\n</template>''')

# 6. Contract test should verify ScanView owns the single-label API call and dialog.
# (Current test already expects these exact symbols, so no rewrite required.)

# 7. Register the new contract and bump mobile version once.
pkg_path = root / 'app/package.json'
pkg = json.loads(pkg_path.read_text(encoding='utf-8'))
pkg['version'] = '1.5.16'
test_cmd = pkg['scripts']['test']
needle = 'src/platform/resource-label-quick-revoke.spec.ts'
if needle not in test_cmd:
    pkg['scripts']['test'] = test_cmd + ' ' + needle
pkg_path.write_text(json.dumps(pkg, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

lock_path = root / 'app/package-lock.json'
lock = json.loads(lock_path.read_text(encoding='utf-8'))
lock['version'] = '1.5.16'
if '' in lock.get('packages', {}):
    lock['packages']['']['version'] = '1.5.16'
lock_path.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

version_path = root / 'app/version.json'
version = json.loads(version_path.read_text(encoding='utf-8'))
version['versionName'] = '1.5.16'
version['versionCode'] = 36
version_path.write_text(json.dumps(version, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

print('label revoke implementation patch applied')
