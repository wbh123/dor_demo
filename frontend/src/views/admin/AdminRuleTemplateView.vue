<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, ObjectSuccessResponse } from '../../api/types'

const templates = ref<DataObject[]>([])
const error = ref('')
const message = ref('')
const dialogOpen = ref(false)
const saving = ref(false)
const revisionSource = ref<DataObject | null>(null)

const form = reactive({
  ruleCode: '',
  ruleName: '',
  holdDurationSeconds: 300,
  holdRenewalLimit: 1,
  allowTeam: true,
  teamMinSize: 2,
  teamMaxSize: 5,
  allowStudentRandom: true,
  unselectedStrategy: 'ADMIN_ALLOCATION',
  ruleVersion: 'phase2-rule-template-v1',
  enabled: true,
  makeDefault: false,
  expectedVersion: 0,
  changeReason: '',
})

const groupedTemplates = computed(() => {
  const groups = new Map<string, DataObject[]>()
  for (const item of templates.value) {
    const code = String(item.rule_code ?? '')
    const group = groups.get(code) ?? []
    group.push(item)
    groups.set(code, group)
  }
  return Array.from(groups.entries()).map(([code, revisions]) => ({ code, revisions }))
})

onMounted(load)

async function load() {
  error.value = ''
  try {
    const response = await api.get<ListSuccessResponse>('/api/v1/admin/batch-rule-templates')
    templates.value = (response.data.data ?? []) as DataObject[]
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '批次规则模板加载失败'
  }
}

function openCreate() {
  revisionSource.value = null
  resetForm()
  form.makeDefault = !templates.value.some((item) => Boolean(item.is_default))
  dialogOpen.value = true
}

function openRevision(item: DataObject) {
  revisionSource.value = item
  form.ruleCode = String(item.rule_code ?? '')
  form.ruleName = String(item.rule_name ?? '')
  form.holdDurationSeconds = Number(item.hold_duration_seconds ?? 300)
  form.holdRenewalLimit = Number(item.hold_renewal_limit ?? 1)
  form.allowTeam = Boolean(item.allow_team)
  form.teamMinSize = Number(item.team_min_size ?? 2)
  form.teamMaxSize = Number(item.team_max_size ?? 5)
  form.allowStudentRandom = Boolean(item.allow_student_random)
  form.unselectedStrategy = String(item.unselected_strategy ?? 'ADMIN_ALLOCATION')
  form.ruleVersion = String(item.rule_version ?? 'phase2-rule-template-v1')
  form.enabled = true
  form.makeDefault = false
  form.expectedVersion = Number(item.version ?? 0)
  form.changeReason = ''
  dialogOpen.value = true
}

function closeDialog() {
  if (saving.value) return
  dialogOpen.value = false
  revisionSource.value = null
}

function resetForm() {
  form.ruleCode = ''
  form.ruleName = ''
  form.holdDurationSeconds = 300
  form.holdRenewalLimit = 1
  form.allowTeam = true
  form.teamMinSize = 2
  form.teamMaxSize = 5
  form.allowStudentRandom = true
  form.unselectedStrategy = 'ADMIN_ALLOCATION'
  form.ruleVersion = 'phase2-rule-template-v1'
  form.enabled = true
  form.makeDefault = false
  form.expectedVersion = 0
  form.changeReason = ''
}

async function save() {
  error.value = ''
  message.value = ''
  if (form.changeReason.trim().length < 2) {
    error.value = '修改原因至少填写2个字符。'
    return
  }
  saving.value = true
  try {
    const common = {
      ruleName: form.ruleName,
      holdDurationSeconds: form.holdDurationSeconds,
      holdRenewalLimit: form.holdRenewalLimit,
      allowTeam: form.allowTeam,
      teamMinSize: form.allowTeam ? form.teamMinSize : 1,
      teamMaxSize: form.allowTeam ? form.teamMaxSize : 1,
      allowStudentRandom: form.allowStudentRandom,
      unselectedStrategy: form.unselectedStrategy,
      ruleVersion: form.ruleVersion,
      enabled: form.enabled,
      makeDefault: form.makeDefault,
      changeReason: form.changeReason.trim(),
    }
    let response
    if (revisionSource.value) {
      response = await api.post<ObjectSuccessResponse>(
        `/api/v1/admin/batch-rule-templates/${Number(revisionSource.value.id)}/revisions`,
        { ...common, expectedVersion: form.expectedVersion },
      )
      message.value = '规则模板新修订已创建，历史修订和已建批次保持不变。'
    } else {
      response = await api.post<ObjectSuccessResponse>('/api/v1/admin/batch-rule-templates', {
        ...common,
        ruleCode: form.ruleCode,
      })
      message.value = '规则模板已创建。'
    }
    if (!response.data.success) throw new Error('规则模板保存失败')
    dialogOpen.value = false
    revisionSource.value = null
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '规则模板保存失败'
  } finally {
    saving.value = false
  }
}

function yesNo(value: unknown) {
  return value ? '是' : '否'
}

function strategyText(value: unknown) {
  return value === 'NONE' ? '不自动处理' : '管理员统一分配'
}
</script>

<template>
  <div class="content-column">
    <div class="page-title rule-template-title">
      <div>
        <span class="eyebrow">BATCH RULE OPERATIONS</span>
        <h2>批次规则模板</h2>
        <p>模板修订不可覆盖。新建批次引用精确修订，并保存独立规则快照。</p>
      </div>
      <button class="button primary" type="button" @click="openCreate">创建规则模板</button>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section v-if="!groupedTemplates.length" class="panel empty-state">
      <h3>暂无规则模板</h3>
      <p>创建第一套模板后，新批次即可直接复用。</p>
    </section>

    <section v-for="group in groupedTemplates" :key="group.code" class="panel rule-template-group">
      <header class="rule-template-group-head">
        <div>
          <span class="eyebrow">{{ group.code }}</span>
          <h3>{{ group.revisions[0]?.rule_name }}</h3>
        </div>
        <span>{{ group.revisions.length }}个修订</span>
      </header>

      <div class="rule-template-grid">
        <article v-for="item in group.revisions" :key="String(item.id)" class="rule-template-card" :class="{ default: item.is_default }">
          <div class="rule-template-card-head">
            <div>
              <strong>修订 {{ item.revision }}</strong>
              <span v-if="item.is_default" class="status-chip active">默认模板</span>
              <span v-else-if="item.enabled" class="status-chip">可选</span>
              <span v-else class="status-chip muted">已停用</span>
            </div>
            <button class="button secondary small" type="button" @click="openRevision(item)">创建新修订</button>
          </div>
          <dl class="rule-template-values">
            <div><dt>临时占用</dt><dd>{{ item.hold_duration_seconds }}秒</dd></div>
            <div><dt>最大续期</dt><dd>{{ item.hold_renewal_limit }}次</dd></div>
            <div><dt>允许组队</dt><dd>{{ yesNo(item.allow_team) }}</dd></div>
            <div><dt>队伍人数</dt><dd>{{ item.team_min_size }}—{{ item.team_max_size }}人</dd></div>
            <div><dt>随机推荐</dt><dd>{{ yesNo(item.allow_student_random) }}</dd></div>
            <div><dt>未选处理</dt><dd>{{ strategyText(item.unselected_strategy) }}</dd></div>
            <div><dt>执行版本</dt><dd>{{ item.rule_version }}</dd></div>
            <div><dt>已引用批次</dt><dd>{{ item.batch_count }}个</dd></div>
          </dl>
          <p class="rule-template-reason">{{ item.change_reason }}</p>
        </article>
      </div>
    </section>

    <div v-if="dialogOpen" class="rule-template-overlay" @click.self="closeDialog">
      <section class="rule-template-dialog" role="dialog" aria-modal="true" aria-labelledby="rule-template-dialog-title">
        <header class="rule-template-dialog-head">
          <div>
            <span class="eyebrow">RULE TEMPLATE REVISION</span>
            <h3 id="rule-template-dialog-title">{{ revisionSource ? '创建新修订' : '创建规则模板' }}</h3>
            <p v-if="revisionSource">来源：{{ revisionSource.rule_code }} 修订{{ revisionSource.revision }}</p>
          </div>
          <button class="icon-button" type="button" aria-label="关闭" :disabled="saving" @click="closeDialog">×</button>
        </header>

        <form class="rule-template-form" @submit.prevent="save">
          <label v-if="!revisionSource"><span>模板编码</span><input v-model.trim="form.ruleCode" class="input" required pattern="[A-Z0-9][A-Z0-9_-]+" maxlength="32" /></label>
          <label><span>模板名称</span><input v-model.trim="form.ruleName" class="input" required maxlength="128" /></label>
          <label><span>临时占用秒数</span><input v-model.number="form.holdDurationSeconds" class="input" type="number" min="30" max="3600" required /></label>
          <label><span>最大续期次数</span><input v-model.number="form.holdRenewalLimit" class="input" type="number" min="0" max="20" required /></label>
          <label class="toggle-field"><input v-model="form.allowTeam" type="checkbox" /><span>允许组队</span></label>
          <label><span>队伍最小人数</span><input v-model.number="form.teamMinSize" class="input" type="number" min="2" max="5" :disabled="!form.allowTeam" required /></label>
          <label><span>队伍最大人数</span><input v-model.number="form.teamMaxSize" class="input" type="number" min="2" max="5" :disabled="!form.allowTeam" required /></label>
          <label class="toggle-field"><input v-model="form.allowStudentRandom" type="checkbox" /><span>允许学生随机推荐</span></label>
          <label><span>未选学生处理</span><select v-model="form.unselectedStrategy" class="input"><option value="ADMIN_ALLOCATION">管理员统一分配</option><option value="NONE">不自动处理</option></select></label>
          <label><span>规则执行版本</span><input v-model.trim="form.ruleVersion" class="input" required maxlength="32" /></label>
          <label class="toggle-field"><input v-model="form.enabled" type="checkbox" /><span>新修订可供批次选择</span></label>
          <label class="toggle-field"><input v-model="form.makeDefault" type="checkbox" /><span>设为默认模板</span></label>
          <label class="rule-template-reason-field"><span>修改原因</span><textarea v-model.trim="form.changeReason" class="input" rows="3" minlength="2" maxlength="500" required placeholder="说明新建或修订原因"></textarea></label>
          <div class="rule-template-actions">
            <button class="button ghost" type="button" :disabled="saving" @click="closeDialog">取消</button>
            <button class="button primary" type="submit" :disabled="saving">{{ saving ? '正在保存…' : revisionSource ? '创建新修订' : '创建模板' }}</button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>
