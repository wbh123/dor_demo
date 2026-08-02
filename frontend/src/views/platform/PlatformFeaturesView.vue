<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  platformApi,
  type FeatureEntitlement,
  type FeatureStateChange,
  type FeatureTargetState,
} from '../../platform/api'

interface FeatureGroup {
  phase: FeatureEntitlement['phase']
  module: string
  features: FeatureEntitlement[]
}

const features = ref<FeatureEntitlement[]>([])
const loading = ref(false)
const error = ref('')
const success = ref('')
const savingCodes = ref<Set<string>>(new Set())

const searchText = ref('')
const phaseFilter = ref('ALL')
const scopeFilter = ref('ALL')
const stateFilter = ref('ALL')
const includeFuture = ref(false)

const batchMode = ref(false)
const draftStates = ref<Record<string, FeatureTargetState>>({})
const batchReason = ref('')

const confirmVisible = ref(false)
const confirmFeature = ref<FeatureEntitlement | null>(null)
const confirmTarget = ref<FeatureTargetState>('ENABLED')
const confirmReason = ref('')
const confirmSaving = ref(false)

const phaseLabels: Record<FeatureEntitlement['phase'], string> = {
  PHASE1: '第一阶段基础功能',
  PHASE2: '第二阶段增强功能',
  PHASE3: '第三阶段规划功能',
}

const scopeLabels: Record<FeatureEntitlement['scope'], string> = {
  ADMIN: '管理端',
  STUDENT: '学生端',
  SHARED: '管理端与学生端',
}

const riskLabels: Record<FeatureEntitlement['riskLevel'], string> = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
}

const sourceLabels: Record<FeatureEntitlement['source'], string> = {
  PLAN_ENABLED: '套餐默认开启',
  PLAN_DISABLED: '套餐默认关闭',
  OVERRIDE_GRANT: '单独增补',
  OVERRIDE_REVOKE: '单独移除',
}

const filteredFeatures = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  return features.value.filter((feature) => {
    if (keyword && !`${feature.featureName} ${feature.featureCode}`.toLowerCase().includes(keyword)) return false
    if (phaseFilter.value !== 'ALL' && feature.phase !== phaseFilter.value) return false
    if (scopeFilter.value !== 'ALL' && feature.scope !== scopeFilter.value) return false
    if (stateFilter.value === 'ENABLED' && !displayEnabled(feature)) return false
    if (stateFilter.value === 'DISABLED' && displayEnabled(feature)) return false
    if (stateFilter.value === 'OVERRIDDEN' && !feature.overrideType) return false
    if (stateFilter.value === 'PLAN' && feature.overrideType) return false
    if (stateFilter.value === 'FUTURE' && feature.enabledInProgram) return false
    return true
  })
})

const groups = computed<FeatureGroup[]>(() => {
  const result = new Map<string, FeatureGroup>()
  for (const feature of filteredFeatures.value) {
    const module = moduleName(feature)
    const key = `${feature.phase}:${module}`
    const group = result.get(key) ?? { phase: feature.phase, module, features: [] }
    group.features.push(feature)
    result.set(key, group)
  }
  return [...result.values()].sort((a, b) => {
    const phaseCompare = a.phase.localeCompare(b.phase)
    if (phaseCompare !== 0) return phaseCompare
    return (a.features[0]?.sortOrder ?? 0) - (b.features[0]?.sortOrder ?? 0)
  })
})

const enabledCount = computed(() => features.value.filter(displayEnabled).length)
const overriddenCount = computed(() => features.value.filter((feature) => Boolean(feature.overrideType)).length)
const futureCount = computed(() => features.value.filter((feature) => !feature.enabledInProgram).length)

const batchChanges = computed<FeatureStateChange[]>(() => features.value
  .filter((feature) => feature.enabledInProgram)
  .map((feature) => ({
    featureCode: feature.featureCode,
    targetState: draftStates.value[feature.featureCode] ?? sourceTarget(feature),
  }))
  .filter((change) => change.targetState !== sourceTarget(findFeature(change.featureCode))))
)

const batchSummary = computed(() => ({
  enabled: batchChanges.value.filter((change) => change.targetState === 'ENABLED').length,
  disabled: batchChanges.value.filter((change) => change.targetState === 'DISABLED').length,
  inherited: batchChanges.value.filter((change) => change.targetState === 'INHERIT').length,
}))

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    features.value = await platformApi.featureEntitlements(includeFuture.value)
    if (batchMode.value) initializeDrafts()
  } catch (cause) {
    showError(cause, '功能授权加载失败')
  } finally {
    loading.value = false
  }
}

function showError(cause: unknown, fallback: string): void {
  error.value = cause instanceof Error ? cause.message : fallback
  success.value = ''
}

function findFeature(featureCode: string): FeatureEntitlement {
  const feature = features.value.find((item) => item.featureCode === featureCode)
  if (!feature) throw new Error(`功能不存在：${featureCode}`)
  return feature
}

function replaceFeature(updated: FeatureEntitlement): void {
  features.value = features.value.map((feature) => feature.featureCode === updated.featureCode ? updated : feature)
}

function sourceTarget(feature: FeatureEntitlement): FeatureTargetState {
  if (feature.source === 'OVERRIDE_GRANT') return 'ENABLED'
  if (feature.source === 'OVERRIDE_REVOKE') return 'DISABLED'
  return 'INHERIT'
}

function effectiveForTarget(feature: FeatureEntitlement, target: FeatureTargetState): boolean {
  if (target === 'INHERIT') return feature.planEnabled
  return target === 'ENABLED'
}

function displayEnabled(feature: FeatureEntitlement): boolean {
  if (!batchMode.value) return feature.effectiveEnabled
  const target = draftStates.value[feature.featureCode] ?? sourceTarget(feature)
  return effectiveForTarget(feature, target)
}

function isChanged(feature: FeatureEntitlement): boolean {
  if (!batchMode.value) return false
  return (draftStates.value[feature.featureCode] ?? sourceTarget(feature)) !== sourceTarget(feature)
}

function moduleName(feature: FeatureEntitlement): string {
  const code = feature.featureCode
  if (/(IDENTITY|STUDENT_CONTACT)/.test(code)) return '用户与身份'
  if (/(DORMITORY|ROOM_LAYOUT|BED_)/.test(code)) return '宿舍与床位'
  if (/(BATCH|RULE_TEMPLATE)/.test(code)) return '批次与规则'
  if (/(PREFERENCE|MATCHING|RECOMMENDATION)/.test(code)) return '偏好与匹配'
  if (/(SELF_SELECTION|THREE_DIMENSIONAL)/.test(code)) return '学生选寝'
  if (/TEAM_/.test(code)) return '组队功能'
  if (/(ALLOCATION|ASSIGNMENT|FAIRNESS)/.test(code)) return '分配与公平性'
  if (/(WELCOME|MULTILINGUAL|NOTIFICATION|MOBILE)/.test(code)) return '学生体验与通知'
  if (/IMPORT_/.test(code)) return '导入与数据质量'
  if (/(AUDIT|STATISTICS|EXCEPTION|HISTORICAL|TREND|REPORT)/.test(code)) return '统计与审计'
  if (/(CONCURRENT|REDIS|PRESSURE|SLOW_QUERY|HEALTH)/.test(code)) return '性能与恢复'
  if (/ROOM_CHANGE/.test(code)) return '换寝管理'
  if (/WAITLIST/.test(code)) return '候补补位'
  if (/(BACKUP|RESTORE|DISASTER|RECOVERY)/.test(code)) return '备份与灾难恢复'
  return '其他功能'
}

function enterBatchMode(): void {
  batchMode.value = true
  batchReason.value = ''
  initializeDrafts()
  success.value = ''
}

function initializeDrafts(): void {
  draftStates.value = Object.fromEntries(features.value.map((feature) => [feature.featureCode, sourceTarget(feature)]))
}

function cancelBatchMode(): void {
  batchMode.value = false
  batchReason.value = ''
  draftStates.value = {}
}

function toggleFeature(feature: FeatureEntitlement): void {
  if (!feature.enabledInProgram || isSaving(feature.featureCode)) return
  const target: FeatureTargetState = displayEnabled(feature) ? 'DISABLED' : 'ENABLED'
  if (batchMode.value) {
    draftStates.value = { ...draftStates.value, [feature.featureCode]: target }
    return
  }
  openConfirmation(feature, target)
}

function restoreDefault(feature: FeatureEntitlement): void {
  if (!feature.enabledInProgram || isSaving(feature.featureCode)) return
  if (batchMode.value) {
    draftStates.value = { ...draftStates.value, [feature.featureCode]: 'INHERIT' }
    return
  }
  openConfirmation(feature, 'INHERIT')
}

function openConfirmation(feature: FeatureEntitlement, target: FeatureTargetState): void {
  confirmFeature.value = feature
  confirmTarget.value = target
  confirmReason.value = ''
  confirmVisible.value = true
  error.value = ''
}

function closeConfirmation(): void {
  if (confirmSaving.value) return
  confirmVisible.value = false
  confirmFeature.value = null
  confirmReason.value = ''
}

async function confirmSingleChange(): Promise<void> {
  const feature = confirmFeature.value
  if (!feature || !confirmReason.value.trim()) return
  confirmSaving.value = true
  markSaving(feature.featureCode, true)
  try {
    const updated = await platformApi.setFeatureState(feature.featureCode, confirmTarget.value, confirmReason.value.trim())
    replaceFeature(updated)
    success.value = `${feature.featureName}已更新为${targetLabel(confirmTarget.value, feature)}`
    error.value = ''
    closeConfirmationAfterSave()
  } catch (cause) {
    showError(cause, '功能授权修改失败')
  } finally {
    confirmSaving.value = false
    markSaving(feature.featureCode, false)
  }
}

function closeConfirmationAfterSave(): void {
  confirmVisible.value = false
  confirmFeature.value = null
  confirmReason.value = ''
}

function applyGroup(group: FeatureGroup, target: FeatureTargetState): void {
  if (!batchMode.value) enterBatchMode()
  const next = { ...draftStates.value }
  for (const feature of group.features) {
    if (feature.enabledInProgram) next[feature.featureCode] = target
  }
  draftStates.value = next
}

async function saveBatch(): Promise<void> {
  if (!batchReason.value.trim() || batchChanges.value.length === 0) return
  const changedCodes = batchChanges.value.map((change) => change.featureCode)
  changedCodes.forEach((code) => markSaving(code, true))
  try {
    const updated = await platformApi.setFeatureStates(batchChanges.value, batchReason.value.trim())
    updated.forEach(replaceFeature)
    success.value = `已在同一事务中保存 ${updated.length} 项功能授权变更`
    error.value = ''
    cancelBatchMode()
  } catch (cause) {
    showError(cause, '批量功能授权保存失败，所有变更均未生效')
  } finally {
    changedCodes.forEach((code) => markSaving(code, false))
  }
}

function markSaving(featureCode: string, saving: boolean): void {
  const next = new Set(savingCodes.value)
  if (saving) next.add(featureCode)
  else next.delete(featureCode)
  savingCodes.value = next
}

function isSaving(featureCode: string): boolean {
  return savingCodes.value.has(featureCode)
}

function targetLabel(target: FeatureTargetState, feature: FeatureEntitlement): string {
  if (target === 'ENABLED') return '开启'
  if (target === 'DISABLED') return '关闭'
  return feature.planEnabled ? '套餐默认开启' : '套餐默认关闭'
}

function clearFilters(): void {
  searchText.value = ''
  phaseFilter.value = 'ALL'
  scopeFilter.value = 'ALL'
  stateFilter.value = 'ALL'
}

onMounted(() => void load())
</script>

<template>
  <section class="feature-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">系统服务管理</p>
        <h1>功能授权</h1>
        <p class="subtitle">通过滑动开关控制单项功能。普通模式立即生效，批量模式统一预览并一次保存。</p>
      </div>
      <div class="header-actions">
        <button v-if="!batchMode" class="primary-button" type="button" @click="enterBatchMode">批量编辑</button>
        <button class="secondary-button" type="button" :disabled="loading" @click="load">刷新</button>
      </div>
    </header>

    <div class="summary-grid">
      <article class="summary-card"><span>已加载功能</span><strong>{{ features.length }}</strong></article>
      <article class="summary-card"><span>当前开启</span><strong>{{ enabledCount }}</strong></article>
      <article class="summary-card"><span>单独覆盖</span><strong>{{ overriddenCount }}</strong></article>
      <article class="summary-card"><span>未来规划</span><strong>{{ futureCount }}</strong></article>
    </div>

    <div class="toolbar panel">
      <label class="search-box">
        <span>搜索</span>
        <input v-model.trim="searchText" type="search" placeholder="输入功能名称或功能代码" />
      </label>
      <label>
        <span>阶段</span>
        <select v-model="phaseFilter">
          <option value="ALL">全部阶段</option>
          <option value="PHASE1">第一阶段</option>
          <option value="PHASE2">第二阶段</option>
          <option value="PHASE3">第三阶段</option>
        </select>
      </label>
      <label>
        <span>使用范围</span>
        <select v-model="scopeFilter">
          <option value="ALL">全部范围</option>
          <option value="ADMIN">管理端</option>
          <option value="STUDENT">学生端</option>
          <option value="SHARED">管理端与学生端</option>
        </select>
      </label>
      <label>
        <span>授权状态</span>
        <select v-model="stateFilter">
          <option value="ALL">全部状态</option>
          <option value="ENABLED">当前开启</option>
          <option value="DISABLED">当前关闭</option>
          <option value="OVERRIDDEN">单独覆盖</option>
          <option value="PLAN">沿用套餐</option>
          <option value="FUTURE">未来功能</option>
        </select>
      </label>
      <label class="future-toggle">
        <input v-model="includeFuture" type="checkbox" @change="load" />
        <span>显示未来功能</span>
      </label>
      <button class="text-button" type="button" @click="clearFilters">清除筛选</button>
    </div>

    <p v-if="error" class="message error-message">{{ error }}</p>
    <p v-if="success" class="message success-message">{{ success }}</p>

    <div v-if="loading" class="empty-state panel">正在加载功能授权…</div>
    <div v-else-if="groups.length === 0" class="empty-state panel">没有符合当前筛选条件的功能。</div>

    <div v-else class="group-list">
      <section v-for="group in groups" :key="`${group.phase}-${group.module}`" class="feature-group panel">
        <header class="group-header">
          <div>
            <span class="phase-badge">{{ phaseLabels[group.phase] }}</span>
            <h2>{{ group.module }}</h2>
            <p>{{ group.features.length }} 项功能</p>
          </div>
          <div class="group-actions">
            <button type="button" @click="applyGroup(group, 'ENABLED')">全部开启</button>
            <button type="button" @click="applyGroup(group, 'DISABLED')">全部关闭</button>
            <button type="button" @click="applyGroup(group, 'INHERIT')">恢复套餐默认</button>
          </div>
        </header>

        <div class="feature-grid">
          <article
            v-for="feature in group.features"
            :key="feature.featureCode"
            class="feature-card"
            :class="{
              disabled: !feature.enabledInProgram,
              changed: isChanged(feature),
              enabled: displayEnabled(feature),
            }"
          >
            <div class="feature-main">
              <div class="feature-title-row">
                <div>
                  <h3>{{ feature.featureName }}</h3>
                  <code>{{ feature.featureCode }}</code>
                </div>
                <button
                  class="switch"
                  :class="{ checked: displayEnabled(feature), loading: isSaving(feature.featureCode) }"
                  type="button"
                  role="switch"
                  :aria-checked="displayEnabled(feature)"
                  :aria-label="`${displayEnabled(feature) ? '关闭' : '开启'}${feature.featureName}`"
                  :disabled="!feature.enabledInProgram || isSaving(feature.featureCode)"
                  @click="toggleFeature(feature)"
                >
                  <span class="switch-thumb" />
                </button>
              </div>

              <div class="tag-row">
                <span class="tag">{{ scopeLabels[feature.scope] }}</span>
                <span class="tag" :class="`risk-${feature.riskLevel.toLowerCase()}`">{{ riskLabels[feature.riskLevel] }}</span>
                <span class="tag source-tag" :class="feature.source.toLowerCase()">{{ sourceLabels[feature.source] }}</span>
                <span v-if="!feature.enabledInProgram" class="tag future-tag">尚未实现</span>
                <span v-if="isChanged(feature)" class="tag changed-tag">待保存</span>
              </div>

              <p class="state-description">
                当前状态：<strong>{{ displayEnabled(feature) ? '已开启' : '已关闭' }}</strong>
                · 套餐默认：{{ feature.planEnabled ? '开启' : '关闭' }}
              </p>
            </div>

            <footer class="feature-footer">
              <span v-if="feature.lastChangedAt">最近覆盖：{{ new Date(feature.lastChangedAt).toLocaleString() }}</span>
              <span v-else>当前未设置单独覆盖</span>
              <button
                type="button"
                class="restore-button"
                :disabled="!feature.enabledInProgram || (!feature.overrideType && !isChanged(feature))"
                @click="restoreDefault(feature)"
              >恢复套餐默认</button>
            </footer>
          </article>
        </div>
      </section>
    </div>

    <div v-if="batchMode" class="batch-bar">
      <div class="batch-summary">
        <strong>批量编辑模式</strong>
        <span>开启 {{ batchSummary.enabled }} 项</span>
        <span>关闭 {{ batchSummary.disabled }} 项</span>
        <span>恢复默认 {{ batchSummary.inherited }} 项</span>
      </div>
      <input v-model.trim="batchReason" type="text" maxlength="500" placeholder="填写本次批量调整原因（必填）" />
      <button type="button" class="secondary-button" @click="cancelBatchMode">取消</button>
      <button
        type="button"
        class="primary-button"
        :disabled="batchChanges.length === 0 || !batchReason"
        @click="saveBatch"
      >保存 {{ batchChanges.length }} 项变更</button>
    </div>

    <div v-if="confirmVisible && confirmFeature" class="dialog-backdrop" @click.self="closeConfirmation">
      <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="feature-change-title">
        <p class="eyebrow">单项授权确认</p>
        <h2 id="feature-change-title">{{ confirmFeature.featureName }}</h2>
        <p>将调整为：<strong>{{ targetLabel(confirmTarget, confirmFeature) }}</strong></p>
        <p class="dialog-help">该变更确认后立即生效，并记录到平台审计。</p>
        <label>
          <span>变更原因</span>
          <textarea v-model.trim="confirmReason" maxlength="500" rows="4" placeholder="例如：本次合同增补开放三维选床功能" autofocus />
        </label>
        <div class="dialog-actions">
          <button type="button" class="secondary-button" :disabled="confirmSaving" @click="closeConfirmation">取消</button>
          <button type="button" class="primary-button" :disabled="!confirmReason || confirmSaving" @click="confirmSingleChange">
            {{ confirmSaving ? '保存中…' : '确认并立即生效' }}
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.feature-page { padding-bottom: 110px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; margin-bottom: 20px; }
.eyebrow { margin: 0 0 4px; color: #2563eb; font-size: 12px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
h1 { margin: 0; font-size: 30px; color: #0f172a; }
.subtitle { margin: 8px 0 0; color: #64748b; }
.header-actions, .group-actions, .dialog-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.panel { background: #fff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 6px 18px rgba(15, 23, 42, .05); }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 16px; }
.summary-card { padding: 18px; background: linear-gradient(145deg, #fff, #f8fafc); border: 1px solid #e2e8f0; border-radius: 14px; }
.summary-card span { display: block; color: #64748b; font-size: 13px; }
.summary-card strong { display: block; margin-top: 6px; color: #0f172a; font-size: 28px; }
.toolbar { display: grid; grid-template-columns: minmax(220px, 2fr) repeat(3, minmax(140px, 1fr)) auto auto; gap: 12px; align-items: end; padding: 16px; margin-bottom: 16px; }
.toolbar label { display: grid; gap: 6px; color: #475569; font-size: 12px; font-weight: 700; }
input, select, textarea { width: 100%; box-sizing: border-box; border: 1px solid #cbd5e1; border-radius: 9px; padding: 10px 11px; background: #fff; color: #0f172a; font: inherit; }
input:focus, select:focus, textarea:focus { outline: 3px solid rgba(37, 99, 235, .14); border-color: #2563eb; }
.future-toggle { display: flex !important; grid-auto-flow: column; align-items: center; gap: 8px !important; padding-bottom: 10px; white-space: nowrap; }
.future-toggle input { width: 16px; height: 16px; }
button { font: inherit; cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .55; }
.primary-button, .secondary-button, .text-button, .group-actions button, .restore-button { border-radius: 9px; padding: 9px 14px; font-weight: 700; }
.primary-button { border: 1px solid #2563eb; background: #2563eb; color: #fff; }
.secondary-button { border: 1px solid #cbd5e1; background: #fff; color: #334155; }
.text-button { border: 0; background: transparent; color: #2563eb; }
.message { padding: 12px 14px; border-radius: 10px; margin: 12px 0; }
.error-message { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }
.success-message { background: #f0fdf4; color: #166534; border: 1px solid #bbf7d0; }
.empty-state { padding: 44px; text-align: center; color: #64748b; }
.group-list { display: grid; gap: 16px; }
.feature-group { overflow: hidden; }
.group-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 18px 20px; border-bottom: 1px solid #e2e8f0; background: #f8fafc; }
.group-header h2 { margin: 7px 0 2px; color: #0f172a; font-size: 19px; }
.group-header p { margin: 0; color: #64748b; font-size: 13px; }
.phase-badge { color: #1d4ed8; font-size: 12px; font-weight: 800; }
.group-actions button { border: 1px solid #cbd5e1; background: #fff; color: #334155; padding: 7px 10px; font-size: 12px; }
.feature-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; padding: 16px; }
.feature-card { display: flex; flex-direction: column; min-height: 190px; border: 1px solid #e2e8f0; border-radius: 14px; background: #fff; transition: border-color .2s, box-shadow .2s, transform .2s; }
.feature-card:hover { border-color: #bfdbfe; box-shadow: 0 8px 20px rgba(37, 99, 235, .08); transform: translateY(-1px); }
.feature-card.enabled { border-left: 4px solid #22c55e; }
.feature-card.changed { outline: 2px solid #f59e0b; outline-offset: -2px; }
.feature-card.disabled { background: #f8fafc; opacity: .72; }
.feature-main { flex: 1; padding: 16px; }
.feature-title-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; }
.feature-title-row h3 { margin: 0 0 5px; color: #0f172a; font-size: 16px; }
code { color: #64748b; font-size: 11px; word-break: break-all; }
.switch { position: relative; flex: 0 0 auto; width: 48px; height: 27px; border: 0; border-radius: 999px; padding: 0; background: #cbd5e1; transition: background .2s; }
.switch.checked { background: #22c55e; }
.switch.loading { animation: pulse 1s infinite; }
.switch-thumb { position: absolute; top: 3px; left: 3px; width: 21px; height: 21px; border-radius: 50%; background: #fff; box-shadow: 0 2px 5px rgba(15, 23, 42, .25); transition: transform .2s; }
.switch.checked .switch-thumb { transform: translateX(21px); }
.tag-row { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 14px; }
.tag { display: inline-flex; align-items: center; border-radius: 999px; padding: 4px 8px; background: #f1f5f9; color: #475569; font-size: 11px; font-weight: 700; }
.risk-medium { background: #fffbeb; color: #b45309; }
.risk-high { background: #fef2f2; color: #b91c1c; }
.override_grant { background: #ecfdf5; color: #047857; }
.override_revoke { background: #fff1f2; color: #be123c; }
.future-tag { background: #f1f5f9; color: #64748b; }
.changed-tag { background: #fff7ed; color: #c2410c; }
.state-description { margin: 14px 0 0; color: #64748b; font-size: 13px; }
.state-description strong { color: #0f172a; }
.feature-footer { display: flex; justify-content: space-between; align-items: center; gap: 10px; padding: 11px 14px; border-top: 1px solid #e2e8f0; color: #64748b; font-size: 11px; }
.restore-button { flex: 0 0 auto; border: 0; background: transparent; color: #2563eb; padding: 5px; font-size: 11px; }
.batch-bar { position: fixed; z-index: 30; left: 270px; right: 30px; bottom: 20px; display: grid; grid-template-columns: auto minmax(220px, 1fr) auto auto; align-items: center; gap: 12px; padding: 14px 16px; border: 1px solid #bfdbfe; border-radius: 14px; background: rgba(255, 255, 255, .97); box-shadow: 0 18px 45px rgba(15, 23, 42, .2); backdrop-filter: blur(10px); }
.batch-summary { display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: center; color: #475569; font-size: 12px; }
.batch-summary strong { width: 100%; color: #0f172a; font-size: 14px; }
.dialog-backdrop { position: fixed; z-index: 60; inset: 0; display: grid; place-items: center; padding: 20px; background: rgba(15, 23, 42, .55); }
.dialog { width: min(520px, 100%); padding: 24px; border-radius: 16px; background: #fff; box-shadow: 0 24px 70px rgba(15, 23, 42, .35); }
.dialog h2 { margin: 0 0 10px; color: #0f172a; }
.dialog p { color: #475569; }
.dialog-help { font-size: 13px; }
.dialog label { display: grid; gap: 7px; margin-top: 16px; color: #334155; font-size: 13px; font-weight: 700; }
.dialog-actions { justify-content: flex-end; margin-top: 18px; }
@keyframes pulse { 50% { opacity: .55; } }
@media (max-width: 1100px) {
  .toolbar { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .feature-grid { grid-template-columns: 1fr; }
  .batch-bar { left: 20px; grid-template-columns: 1fr 1fr; }
}
@media (max-width: 700px) {
  .page-header, .group-header { align-items: stretch; flex-direction: column; }
  .summary-grid, .toolbar { grid-template-columns: 1fr 1fr; }
  .search-box { grid-column: 1 / -1; }
  .feature-grid { padding: 10px; }
  .feature-footer { align-items: flex-start; flex-direction: column; }
  .batch-bar { left: 10px; right: 10px; bottom: 10px; grid-template-columns: 1fr; }
}
@media (max-width: 480px) {
  .summary-grid, .toolbar { grid-template-columns: 1fr; }
}
</style>
