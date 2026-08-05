<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

const rows = ref<DataObject[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')
const editing = ref<DataObject | null>(null)
const targetEnabled = ref(false)
const reason = ref('')
const highRiskConfirmed = ref(false)
const { subtitle, translateError } = useI18n()

const groupedRows = computed(() => {
  const groups = new Map<string, { name: string; rows: DataObject[] }>()
  for (const row of rows.value) {
    const code = String(row.category_code ?? 'OTHER')
    const group = groups.get(code) ?? { name: String(row.category_name ?? code), rows: [] }
    group.rows.push(row)
    groups.set(code, group)
  }
  return [...groups.entries()].map(([code, group]) => ({ code, ...group }))
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/admin/settings/features')
    rows.value = (response.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    loading.value = false
  }
}

function statusText(row: DataObject) {
  const reason = String(row.unavailableReason ?? '')
  if (reason === 'NOT_IMPLEMENTED') return '程序尚未实现'
  if (reason === 'SYSTEM_NOT_GRANTED') return '系统未授权'
  if (reason === 'SCHOOL_DISABLED') return '本校已关闭'
  if (reason === 'BUSINESS_STATE_BLOCKED') return '当前业务流程不可用'
  return Boolean(row.effectiveEnabled) ? '当前已启用' : '当前不可用'
}

function statusClass(row: DataObject) {
  if (Boolean(row.effectiveEnabled)) return 'enabled'
  const reason = String(row.unavailableReason ?? '')
  return reason === 'SCHOOL_DISABLED' ? 'school-disabled' : 'blocked'
}

function categoryLabel(row: DataObject) {
  return `${String(row.category_code ?? '')}类`
}

function riskLabel(row: DataObject) {
  return ({ LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' } as Record<string, string>)[String(row.risk_level ?? 'LOW')] ?? String(row.risk_level)
}

function canChange(row: DataObject) {
  return Boolean(row.school_controllable) && Boolean(row.systemGranted)
}

function openChange(row: DataObject, enabled: boolean) {
  editing.value = row
  targetEnabled.value = enabled
  reason.value = ''
  highRiskConfirmed.value = false
  error.value = ''
  message.value = ''
}

function closeChange() {
  if (saving.value) return
  editing.value = null
}

async function saveChange() {
  const row = editing.value
  if (!row || !reason.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    await api.put<ObjectSuccessResponse>(
      `/api/v1/admin/settings/features/${encodeURIComponent(String(row.feature_code))}`,
      {
        enabled: targetEnabled.value,
        expectedVersion: Number(row.version ?? 0),
        reason: reason.value.trim(),
        highRiskConfirmed: highRiskConfirmed.value,
      },
    )
    message.value = `${String(row.feature_name)}已${targetEnabled.value ? '启用' : '关闭'}`
    editing.value = null
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="content-column">
    <header class="page-title split-title">
      <div>
        <span class="eyebrow">{{ subtitle('学校功能控制', 'SCHOOL FEATURE CONTROL') }}</span>
        <h2>功能启用设置</h2>
        <p>系统授权决定可用上限，本页面只控制本校是否启用。关闭功能不会删除已有历史数据。</p>
      </div>
      <button class="button secondary" :disabled="loading" @click="load">重新加载</button>
    </header>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>
    <p v-if="loading" class="panel empty-state">正在加载功能状态…</p>

    <template v-else>
      <section v-for="group in groupedRows" :key="group.code" class="panel feature-group">
        <div class="feature-group-title">
          <div><span class="eyebrow">{{ group.code }}类权限</span><h3>{{ group.name }}</h3></div>
          <span>{{ group.rows.length }}项功能</span>
        </div>
        <div class="feature-table-wrap">
          <table class="feature-table">
            <thead><tr><th>功能</th><th>分类</th><th>系统授权</th><th>本校状态</th><th>最终状态</th><th>控制范围</th><th>风险</th><th>最近修改</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in group.rows" :key="String(row.feature_code)">
                <td><strong>{{ row.feature_name }}</strong><small>{{ row.feature_code }}</small></td>
                <td>{{ categoryLabel(row) }}</td>
                <td><span class="state-chip" :class="row.systemGranted ? 'enabled' : 'blocked'">{{ row.systemGranted ? '已授权' : '未授权' }}</span></td>
                <td>{{ row.schoolEnabled ? '已启用' : '已关闭' }}</td>
                <td><span class="state-chip" :class="statusClass(row)">{{ statusText(row) }}</span></td>
                <td>{{ row.school_controllable ? '学校可控制' : '系统统一控制' }}</td>
                <td>{{ riskLabel(row) }}</td>
                <td><span>{{ row.updated_at || '尚未单独修改' }}</span><small v-if="row.updated_by_name">{{ row.updated_by_name }}</small></td>
                <td>
                  <button
                    v-if="canChange(row)"
                    class="button small"
                    :class="row.schoolEnabled ? 'danger-soft' : 'primary'"
                    :disabled="!row.enabled_in_program && !row.schoolEnabled"
                    @click="openChange(row, !Boolean(row.schoolEnabled))"
                  >{{ row.schoolEnabled ? '关闭' : '启用' }}</button>
                  <span v-else class="muted-text">不可修改</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <div v-if="editing" class="modal-overlay" @click.self="closeChange">
      <section class="modal-card feature-change-dialog" role="dialog" aria-modal="true">
        <span class="eyebrow">功能状态修改</span>
        <h3>{{ targetEnabled ? '启用' : '关闭' }}「{{ editing.feature_name }}」</h3>
        <p>当前最终状态：{{ statusText(editing) }}。修改只影响本校，不能突破系统管理员授权范围。</p>
        <label><span>修改原因</span><textarea v-model="reason" class="input reason-input" maxlength="500" placeholder="请说明本次修改的业务原因" /></label>
        <label v-if="String(editing.risk_level) === 'HIGH'" class="high-risk-confirm">
          <input v-model="highRiskConfirmed" type="checkbox" />
          <span>我已确认这是高风险功能修改，理解其可能影响正在进行的业务。</span>
        </label>
        <div class="button-row dialog-actions">
          <button class="button ghost" :disabled="saving" @click="closeChange">取消</button>
          <button class="button primary" :disabled="saving || !reason.trim() || (String(editing.risk_level) === 'HIGH' && !highRiskConfirmed)" @click="saveChange">{{ saving ? '正在保存…' : '确认修改' }}</button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.feature-group{padding:20px}.feature-group-title{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-bottom:16px}.feature-group-title h3{margin:5px 0 0}.feature-group-title>span{color:var(--muted)}.feature-table-wrap{overflow:auto}.feature-table{width:100%;min-width:1180px;border-collapse:collapse}.feature-table th,.feature-table td{padding:12px 10px;border-bottom:1px solid var(--line);text-align:left;vertical-align:middle}.feature-table th{font-size:12px;color:var(--muted);font-weight:700}.feature-table td strong,.feature-table td small{display:block}.feature-table td small{margin-top:4px;color:var(--muted);font-size:11px}.state-chip{display:inline-flex;padding:5px 9px;border-radius:999px;font-size:12px;font-weight:700}.state-chip.enabled{background:#e5f7ef;color:#14815f}.state-chip.school-disabled{background:#fff3d8;color:#956516}.state-chip.blocked{background:#f4e8e8;color:#9f4141}.muted-text{color:var(--muted);font-size:12px}.button.small{padding:7px 12px}.button.danger-soft{background:#fff0f0;color:#a53b3b;border-color:#efc9c9}.feature-change-dialog{width:min(560px,calc(100vw - 32px));padding:24px}.feature-change-dialog h3{margin:7px 0}.feature-change-dialog p{color:var(--muted);line-height:1.6}.feature-change-dialog label>span{display:block;margin-bottom:7px;font-weight:700}.reason-input{min-height:120px;resize:vertical}.high-risk-confirm{display:flex!important;align-items:flex-start;gap:10px;margin-top:15px;padding:13px;border-radius:13px;background:#fff2e4;color:#8a521c}.high-risk-confirm span{margin:0!important;font-weight:600!important;line-height:1.5}.dialog-actions{justify-content:flex-end;margin-top:20px}
</style>
