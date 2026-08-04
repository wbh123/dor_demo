<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { platformApi, type FeatureEntitlement, type FeatureTargetState } from '../../platform/api'

interface PermissionGroup {
  permissionClass: string
  module: string
  features: FeatureEntitlement[]
}

const features = ref<FeatureEntitlement[]>([])
const loading = ref(false)
const error = ref('')
const success = ref('')
const searchText = ref('')
const classFilter = ref('ALL')
const scopeFilter = ref('ALL')
const stateFilter = ref('ALL')
const includeOptional = ref(true)
const savingCodes = ref<Set<string>>(new Set())
const confirmFeature = ref<FeatureEntitlement | null>(null)
const confirmTarget = ref<FeatureTargetState>('ENABLED')
const confirmReason = ref('')
const batchSelection = ref<string[]>([])
const batchTarget = ref<FeatureTargetState | null>(null)
const batchReason = ref('')
const batchSaving = ref(false)

const bedSelectionFeature = computed(() => features.value.find((feature) => feature.featureCode === 'P2_BED_SELECTION_MODE') ?? null)
const filteredFeatures = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  return features.value.filter((feature) => {
    if (keyword && !feature.featureName.toLowerCase().includes(keyword)) return false
    if (classFilter.value !== 'ALL' && permissionClass(feature) !== classFilter.value) return false
    if (scopeFilter.value !== 'ALL' && feature.scope !== scopeFilter.value) return false
    if (stateFilter.value === 'ENABLED' && !feature.effectiveEnabled) return false
    if (stateFilter.value === 'DISABLED' && feature.effectiveEnabled) return false
    if (!includeOptional.value && permissionClass(feature) === 'C') return false
    return true
  })
})
const groups = computed<PermissionGroup[]>(() => {
  const result = new Map<string, PermissionGroup>()
  for (const feature of filteredFeatures.value) {
    const permission = permissionClass(feature)
    const module = moduleName(feature)
    const key = `${permission}:${module}`
    const group = result.get(key) ?? { permissionClass: permission, module, features: [] }
    group.features.push(feature)
    result.set(key, group)
  }
  return [...result.values()].sort((a, b) => a.permissionClass.localeCompare(b.permissionClass) || a.module.localeCompare(b.module))
})
const enabledCount = computed(() => features.value.filter((feature) => feature.effectiveEnabled).length)
const batchSelectableFeatures = computed(() => filteredFeatures.value.filter((feature) => feature.enabledInProgram))
const classCounts = computed(() => ({
  A: features.value.filter((feature) => permissionClass(feature) === 'A').length,
  B: features.value.filter((feature) => permissionClass(feature) === 'B').length,
  C: features.value.filter((feature) => permissionClass(feature) === 'C').length,
}))

onMounted(load)

async function load() {
  loading.value = true; error.value = ''
  try {
    features.value = await platformApi.featureEntitlements(true)
    batchSelection.value = batchSelection.value.filter((code) => features.value.some((feature) => feature.featureCode === code && feature.enabledInProgram))
  }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '功能授权加载失败' }
  finally { loading.value = false }
}

function permissionClass(feature: FeatureEntitlement) {
  if (!feature.enabledInProgram) return 'C'
  return ({ PHASE1: 'A', PHASE2: 'B', PHASE3: 'C' } as Record<string,string>)[feature.phase] ?? 'C'
}
function permissionTitle(value: string) { return `${value}类权限` }
function permissionDescription(value: string) {
  return value === 'A' ? '系统日常运行所需的基础业务能力' : value === 'B' ? '用于提升效率和体验的增强能力' : '可按需要启用的可选能力'
}
function scopeText(value: FeatureEntitlement['scope']) { return value === 'ADMIN' ? '管理端' : value === 'STUDENT' ? '学生端' : '管理端与学生端' }
function moduleName(feature: FeatureEntitlement) {
  const code = feature.featureCode
  if (/(IDENTITY|STUDENT_CONTACT|IMPORT_STUDENT)/.test(code)) return '学生与身份'
  if (/(DORMITORY|ROOM_LAYOUT|BED_|IMPORT_ROOM)/.test(code)) return '宿舍与床位'
  if (/(BATCH|RULE_TEMPLATE)/.test(code)) return '批次与规则'
  if (/(PREFERENCE|MATCHING|RECOMMENDATION)/.test(code)) return '偏好与匹配'
  if (/(SELF_SELECTION|THREE_DIMENSIONAL)/.test(code)) return '学生选寝'
  if (/TEAM_/.test(code)) return '组队功能'
  if (/(ALLOCATION|ASSIGNMENT|FAIRNESS)/.test(code)) return '分配管理'
  if (/(WELCOME|MULTILINGUAL|NOTIFICATION|MOBILE)/.test(code)) return '学生体验'
  if (/(AUDIT|STATISTICS|EXCEPTION|REPORT)/.test(code)) return '统计与审计'
  if (/(CONCURRENT|REDIS|PRESSURE|SLOW_QUERY|HEALTH)/.test(code)) return '运行保障'
  if (/ROOM_CHANGE/.test(code)) return '换寝管理'
  if (/WAITLIST/.test(code)) return '候补补位'
  if (/(BACKUP|RESTORE|DISASTER|RECOVERY)/.test(code)) return '备份恢复'
  return '其他业务'
}
function isSaving(feature: FeatureEntitlement) { return savingCodes.value.has(feature.featureCode) }
function openChange(feature: FeatureEntitlement, target?: FeatureTargetState) {
  if (!feature.enabledInProgram) return
  confirmFeature.value = feature
  confirmTarget.value = target ?? (feature.effectiveEnabled ? 'DISABLED' : 'ENABLED')
  confirmReason.value = ''
  error.value = ''; success.value = ''
}
async function saveChange() {
  const feature = confirmFeature.value
  if (!feature || !confirmReason.value.trim()) return
  const next = new Set(savingCodes.value); next.add(feature.featureCode); savingCodes.value = next
  try {
    const updated = await platformApi.setFeatureState(feature.featureCode, confirmTarget.value, confirmReason.value.trim())
    features.value = features.value.map((item) => item.featureCode === updated.featureCode ? updated : item)
    success.value = `${feature.featureName}已${updated.effectiveEnabled ? '开启' : '关闭'}。`
    confirmFeature.value = null; confirmReason.value = ''
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '功能授权修改失败' }
  finally { const done = new Set(savingCodes.value); done.delete(feature.featureCode); savingCodes.value = done }
}
async function restoreDefault(feature: FeatureEntitlement) {
  confirmFeature.value = feature
  confirmTarget.value = 'INHERIT'
  confirmReason.value = ''
}
function toggleBatchSelection(featureCode: string) {
  batchSelection.value = batchSelection.value.includes(featureCode)
    ? batchSelection.value.filter((code) => code !== featureCode)
    : [...batchSelection.value, featureCode]
}
function selectCurrentResults() {
  batchSelection.value = [...new Set([...batchSelection.value, ...batchSelectableFeatures.value.map((feature) => feature.featureCode)])]
}
function openBatchChange(target: FeatureTargetState) {
  if (!batchSelection.value.length) return
  batchTarget.value = target
  batchReason.value = ''
  error.value = ''; success.value = ''
}
async function saveBatchChange() {
  if (!batchTarget.value || !batchSelection.value.length || !batchReason.value.trim()) return
  batchSaving.value = true
  try {
    const updated = await platformApi.setFeatureStates(
      batchSelection.value.map((featureCode) => ({ featureCode, targetState: batchTarget.value as FeatureTargetState })),
      batchReason.value.trim(),
    )
    const replacements = new Map(updated.map((feature) => [feature.featureCode, feature]))
    features.value = features.value.map((feature) => replacements.get(feature.featureCode) ?? feature)
    success.value = `已批量${batchTarget.value === 'ENABLED' ? '开启' : batchTarget.value === 'DISABLED' ? '关闭' : '恢复'}${updated.length}项功能。`
    batchSelection.value = []
    batchTarget.value = null
    batchReason.value = ''
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '批量功能授权失败' }
  finally { batchSaving.value = false }
}
</script>

<template>
  <section class="feature-page">
    <header class="page-heading"><div><p class="eyebrow">系统服务管理</p><h1>功能授权</h1><p>按A、B、C三类权限管理当前学校可使用的业务功能，页面不展示内部功能编号。</p></div><button type="button" :disabled="loading" @click="load">{{ loading ? '正在刷新…' : '刷新授权' }}</button></header>
    <p v-if="error" class="notice error">{{ error }}</p><p v-if="success" class="notice success">{{ success }}</p>

    <div class="summary-grid"><article><span>已开启功能</span><strong>{{ enabledCount }}</strong><small>当前学校可使用</small></article><article><span>A类权限</span><strong>{{ classCounts.A }}</strong><small>基础业务能力</small></article><article><span>B类权限</span><strong>{{ classCounts.B }}</strong><small>增强业务能力</small></article><article><span>C类权限</span><strong>{{ classCounts.C }}</strong><small>可选业务能力</small></article></div>

    <section v-if="bedSelectionFeature" class="core-control panel" :class="{ enabled: bedSelectionFeature.effectiveEnabled }">
      <div><p class="eyebrow">核心模式开关</p><h2>学生选择具体床位</h2><p>开启后，管理员可以让学生直接选择床位；关闭后，学生只选择寝室，床位由入住成员协商。</p></div>
      <div><strong>{{ bedSelectionFeature.effectiveEnabled ? '已开启' : '已关闭' }}</strong><button type="button" role="switch" :aria-checked="bedSelectionFeature.effectiveEnabled" :disabled="isSaving(bedSelectionFeature)" @click="openChange(bedSelectionFeature)"><i /></button></div>
    </section>

    <section class="panel toolbar">
      <label class="search"><span>搜索功能</span><input v-model.trim="searchText" type="search" placeholder="输入业务功能名称" /></label>
      <label><span>权限类别</span><select v-model="classFilter"><option value="ALL">全部类别</option><option value="A">A类权限</option><option value="B">B类权限</option><option value="C">C类权限</option></select></label>
      <label><span>使用端</span><select v-model="scopeFilter"><option value="ALL">全部</option><option value="ADMIN">管理端</option><option value="STUDENT">学生端</option><option value="SHARED">管理端与学生端</option></select></label>
      <label><span>当前状态</span><select v-model="stateFilter"><option value="ALL">全部状态</option><option value="ENABLED">已开启</option><option value="DISABLED">已关闭</option></select></label>
      <label class="optional-check"><input v-model="includeOptional" type="checkbox" /><span>显示C类可选功能</span></label>
    </section>

    <section class="panel batch-controls">
      <div><strong>批量功能授权</strong><span>已选择 {{ batchSelection.length }} 项；仅对当前筛选结果中的已实现功能生效。</span></div>
      <div class="batch-control-actions"><button class="secondary" type="button" @click="selectCurrentResults">选择当前结果</button><button class="secondary" type="button" :disabled="!batchSelection.length" @click="batchSelection = []">清空选择</button><button type="button" :disabled="!batchSelection.length" @click="openBatchChange('ENABLED')">批量开启</button><button class="danger-action" type="button" :disabled="!batchSelection.length" @click="openBatchChange('DISABLED')">批量关闭</button></div>
    </section>

    <div class="permission-groups">
      <section v-for="group in groups" :key="`${group.permissionClass}-${group.module}`" class="panel permission-group">
        <header><div class="permission-heading-line"><div class="permission-heading-title"><span class="class-badge" :class="`class-${group.permissionClass}`">{{ permissionTitle(group.permissionClass) }}</span><h2>{{ group.module }}</h2></div><span class="enabled-summary">{{ group.features.filter((item) => item.effectiveEnabled).length }} / {{ group.features.length }} 已开启</span></div><p>{{ permissionDescription(group.permissionClass) }}</p></header>
        <div class="feature-list">
          <article v-for="feature in group.features" :key="feature.featureCode" :class="{ disabled: !feature.effectiveEnabled, unavailable: !feature.enabledInProgram }">
            <label class="batch-feature-check" :title="feature.enabledInProgram ? '加入批量操作' : '当前程序尚未实现'"><input type="checkbox" :disabled="!feature.enabledInProgram" :checked="batchSelection.includes(feature.featureCode)" @change="toggleBatchSelection(feature.featureCode)" /></label>
            <div class="feature-info"><div><strong>{{ feature.featureName }}</strong><span>{{ scopeText(feature.scope) }}</span></div><p v-if="!feature.enabledInProgram">当前版本尚未提供此可选功能。</p><p v-else>{{ feature.effectiveEnabled ? '当前学校可以使用此功能。' : '当前学校暂未开启此功能。' }}</p><small v-if="feature.overrideType">本功能已单独调整，不再完全跟随套餐默认设置。</small></div>
            <div class="feature-actions"><button v-if="feature.overrideType && feature.enabledInProgram" class="restore" type="button" @click="restoreDefault(feature)">恢复套餐设置</button><button v-if="feature.enabledInProgram" class="switch" :class="{ checked: feature.effectiveEnabled }" type="button" role="switch" :aria-checked="feature.effectiveEnabled" :disabled="isSaving(feature)" @click="openChange(feature)"><i /></button><span v-else>暂不可用</span></div>
          </article>
        </div>
      </section>
      <p v-if="!groups.length" class="panel empty">没有符合条件的功能。</p>
    </div>

    <div v-if="batchTarget" class="modal-backdrop" @click.self="batchTarget = null"><section class="dialog"><h2>{{ batchTarget === 'ENABLED' ? '批量开启功能' : batchTarget === 'DISABLED' ? '批量关闭功能' : '批量恢复默认设置' }}</h2><p>本次将调整 {{ batchSelection.length }} 项功能，保存后立即影响当前学校。</p><label><span>变更原因</span><textarea v-model.trim="batchReason" rows="4" maxlength="500" placeholder="说明本次批量授权的业务原因" /></label><div><button class="secondary" type="button" @click="batchTarget = null">取消</button><button type="button" :disabled="!batchReason.trim() || batchSaving" @click="saveBatchChange">{{ batchSaving ? '正在保存…' : '确认批量保存' }}</button></div></section></div>

    <div v-if="confirmFeature" class="modal-backdrop" @click.self="confirmFeature = null"><section class="dialog"><span class="class-badge" :class="`class-${permissionClass(confirmFeature)}`">{{ permissionTitle(permissionClass(confirmFeature)) }}</span><h2>{{ confirmTarget === 'INHERIT' ? '恢复套餐默认设置' : confirmTarget === 'ENABLED' ? '开启功能' : '关闭功能' }}</h2><p>功能：{{ confirmFeature.featureName }}</p><p>{{ confirmTarget === 'INHERIT' ? '恢复后将由当前套餐决定是否开启。' : confirmTarget === 'ENABLED' ? '保存后当前学校即可使用此功能。' : '保存后当前学校将不能继续使用此功能。' }}</p><label><span>变更原因</span><textarea v-model.trim="confirmReason" rows="4" maxlength="500" placeholder="说明为什么需要本次调整" /></label><div><button class="secondary" type="button" @click="confirmFeature = null">取消</button><button type="button" :disabled="!confirmReason.trim()" @click="saveChange">确认保存</button></div></section></div>
  </section>
</template>

<style scoped>
.feature-page{display:grid;gap:22px}.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.page-heading h1{margin:0 0 8px}.page-heading p{margin:0;color:#69758b}.page-heading>button,.dialog>div button{padding:10px 15px;border:0;border-radius:10px;color:#fff;background:#1d5dd8;cursor:pointer}.summary-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px}.summary-grid article,.panel{padding:20px;border:1px solid #dde4ef;border-radius:18px;background:#fff;box-shadow:0 12px 28px rgba(22,43,82,.06)}.summary-grid span,.summary-grid small{display:block;color:#69758b;font-size:.76rem}.summary-grid strong{display:block;margin:10px 0 6px;font-size:1.8rem}.core-control{display:flex;justify-content:space-between;align-items:center;gap:20px;border-color:#cfdaf0;background:linear-gradient(135deg,#f7faff,#edf3ff)}.core-control.enabled{border-color:#b9e2d5;background:linear-gradient(135deg,#f4fcf9,#e8f8f2)}.core-control h2{margin:0 0 6px}.core-control p{margin:0;color:#69758b}.core-control>div:last-child{display:flex;align-items:center;gap:12px}.core-control button,.switch{width:50px;height:28px;padding:3px;border:0;border-radius:999px;background:#c7d0df;cursor:pointer}.core-control.enabled button,.switch.checked{background:#1d5dd8}.core-control button i,.switch i{display:block;width:22px;height:22px;border-radius:50%;background:#fff;transition:.18s}.core-control.enabled button i,.switch.checked i{transform:translateX(22px)}.toolbar{display:grid;grid-template-columns:minmax(240px,1fr) repeat(3,180px);gap:12px;align-items:end}.toolbar label{display:grid;gap:7px}.toolbar label>span{color:#526078;font-size:.75rem;font-weight:700}.toolbar input,.toolbar select{width:100%;padding:10px 11px;border:1px solid #d7dfeb;border-radius:10px;background:#fff}.optional-check{grid-column:1/-1;display:flex!important;align-items:center;gap:8px!important}.optional-check input{width:17px;height:17px}.permission-groups{display:grid;gap:18px}.permission-group>header{display:grid;gap:5px;margin-bottom:16px}.permission-heading-line{display:flex;align-items:center;justify-content:space-between;gap:18px}.permission-heading-title{display:flex;align-items:center;gap:9px;min-width:0}.permission-group header h2{margin:0;font-size:1.1rem}.permission-group header p,.enabled-summary{margin:0;color:#69758b;font-size:.75rem}.enabled-summary{margin-left:auto;white-space:nowrap}.class-badge{display:inline-flex;padding:5px 9px;border-radius:999px;font-size:.68rem;font-weight:800}.class-A{color:#17664f;background:#e8f8f2}.class-B{color:#315c9e;background:#edf3ff}.class-C{color:#7a5116;background:#fff5df}.feature-list{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:11px}.feature-list article{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:flex-start;gap:12px;padding:15px;border:1px solid #e2e8f1;border-radius:13px;background:#fbfcfe}.feature-list article.disabled{background:#f8f9fb}.feature-list article.unavailable{opacity:.72}.feature-info>div{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.feature-info>div span{padding:3px 7px;border-radius:999px;color:#526078;background:#eef2f7;font-size:.65rem}.feature-info p,.feature-info small{display:block;margin:6px 0 0;color:#69758b;font-size:.72rem}.feature-actions{display:flex;align-items:center;gap:8px}.feature-actions .restore{padding:7px 9px;border:1px solid #d7dfeb;border-radius:8px;color:#526078;background:#fff;cursor:pointer;font-size:.68rem}.feature-actions>span{color:#69758b;font-size:.7rem}.modal-backdrop{position:fixed;inset:0;z-index:100;display:grid;place-items:center;padding:20px;background:rgba(12,24,48,.68);backdrop-filter:blur(8px)}.dialog{width:min(520px,100%);padding:24px;border-radius:18px;background:#fff;box-shadow:0 28px 70px rgba(0,0,0,.25)}.dialog h2{margin:13px 0 8px}.dialog p{color:#69758b}.dialog label{display:grid;gap:7px}.dialog label span{color:#526078;font-size:.76rem;font-weight:700}.dialog textarea{width:100%;padding:11px 12px;border:1px solid #d7dfeb;border-radius:10px}.dialog>div{display:flex;justify-content:flex-end;gap:10px;margin-top:16px}.dialog>div .secondary{color:#315c9e;background:#edf3ff}.notice{margin:0;padding:12px 14px;border-radius:10px}.notice.error{color:#9b2838;background:#fff0f2}.notice.success{color:#17664f;background:#edf9f5}.empty{text-align:center;color:#69758b}.eyebrow{margin:0 0 7px;color:#64789c;font-size:.68rem;font-weight:700;letter-spacing:.14em}@media(max-width:1000px){.summary-grid{grid-template-columns:repeat(2,1fr)}.toolbar{grid-template-columns:1fr 1fr}}@media(max-width:680px){.page-heading,.core-control,.permission-group>header{display:grid}.summary-grid,.toolbar{grid-template-columns:1fr}.feature-list{grid-template-columns:1fr}}
.batch-controls{display:flex;align-items:center;justify-content:space-between;gap:18px}.batch-controls>div:first-child{display:grid;gap:4px}.batch-controls span{color:#69758b;font-size:.75rem}.batch-control-actions{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}.batch-control-actions button{padding:9px 12px;border:0;border-radius:10px;color:#fff;background:#1d5dd8;cursor:pointer}.batch-control-actions .secondary{color:#315c9e;background:#edf3ff}.batch-control-actions .danger-action{background:#b4233a}.batch-feature-check{padding-top:2px}.batch-feature-check input{width:17px;height:17px}.dialog label{display:grid;gap:7px}.dialog textarea{padding:10px;border:1px solid #d7dfeb;border-radius:10px;resize:vertical}@media(max-width:760px){.batch-controls,.permission-heading-line{align-items:flex-start;flex-direction:column}.batch-control-actions{justify-content:flex-start}.enabled-summary{margin-left:0}}
</style>
