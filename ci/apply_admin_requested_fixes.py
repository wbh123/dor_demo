from pathlib import Path

ROOT = Path('private-repo')

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')

def replace(path, old, new, count=1):
    text = read(path)
    if old not in text:
        if new in text:
            return
        raise SystemExit(f'missing replacement marker in {path}: {old[:120]!r}')
    write(path, text.replace(old, new, count))

# 1. Explicit no-access administrator profile semantics.
replace('backend-java/server/src/main/java/com/wust/dormitory/accountadmin/AccountAdminGrantService.java',
'''        if (scopes == null || scopes.isEmpty()) {
            throw bad("ADMIN_SCOPE_REQUIRED", "岗位授权至少需要一个数据范围");
        }
        Set<String> allowed = Set.of(allowedScopeTypes.split(","));''',
'''        if (scopes == null) {
            throw bad("ADMIN_SCOPE_REQUIRED", "岗位授权必须明确选择数据范围或无权限");
        }
        if (scopes.isEmpty()) return;
        Set<String> allowed = Set.of(allowedScopeTypes.split(","));''')
replace('backend-java/server/src/main/java/com/wust/dormitory/accountadmin/AccountAdminAccountService.java',
'''        if (command.scopes() == null || command.scopes().isEmpty()) {
            throw bad("BUSINESS_ADMIN_INITIAL_SCOPE_REQUIRED", "业务管理员或宿管人员创建时必须同时配置首个数据范围");
        }''',
'''        if (command.scopes() == null) {
            throw bad("BUSINESS_ADMIN_INITIAL_SCOPE_REQUIRED", "业务管理员或宿管人员创建时必须明确选择首个数据范围或无权限");
        }''')

p = 'frontend/src/account/useAccountAdminConsole.ts'
replace(p, "export const SUPPORTED_SCOPE_TYPES = ['SCHOOL', 'CAMPUS', 'BUILDING', 'FLOOR', 'MAJOR'] as const",
           "export const SUPPORTED_SCOPE_TYPES = ['SCHOOL', 'CAMPUS', 'BUILDING', 'FLOOR', 'ROOM', 'MAJOR'] as const")
replace(p, "  FLOOR: '楼层',\n  MAJOR: '专业',", "  FLOOR: '楼层',\n  ROOM: '寝室',\n  MAJOR: '专业',")
replace(p, "  clientScope: 'BOTH' as ClientScope,\n  scopeType: 'BUILDING',",
           "  clientScope: 'BOTH' as ClientScope,\n  scopeMode: 'NONE' as 'NONE' | 'SCHOOL' | 'SPECIFIC',\n  scopeType: 'BUILDING',")
replace(p, '''async function configureCreateTemplate() {
  accountForm.clientScope = preferredClient(accountForm.templateVersionId)
  accountForm.scopeType = preferredScopeType(accountForm.templateVersionId)
  accountForm.scopeRefId = null
  await loadScopeOptions(accountForm.scopeType, createScopeOptions)
}''',
'''async function configureCreateTemplate() {
  accountForm.clientScope = preferredClient(accountForm.templateVersionId)
  accountForm.scopeMode = 'NONE'
  accountForm.scopeType = preferredScopeType(accountForm.templateVersionId)
  accountForm.scopeRefId = null
  await loadScopeOptions(accountForm.scopeType, createScopeOptions)
}''')
replace(p, "  accountForm.clientScope = 'BOTH'\n  accountForm.scopeType = 'SCHOOL'",
           "  accountForm.clientScope = 'BOTH'\n  accountForm.scopeMode = 'NONE'\n  accountForm.scopeType = 'SCHOOL'")
replace(p, '''    if (accountForm.scopeType !== 'SCHOOL' && !accountForm.scopeRefId) {
      return setError(`请选择具体${scopeLabels[accountForm.scopeType] ?? '数据范围'}。`)
    }''',
'''    if (accountForm.scopeMode === 'SPECIFIC' && !accountForm.scopeRefId) {
      return setError(`请选择具体${scopeLabels[accountForm.scopeType] ?? '数据范围'}。`)
    }''')
replace(p, '''        scopes: [{
          scopeType: accountForm.scopeType,
          scopeRefId: accountForm.scopeType === 'SCHOOL' ? null : accountForm.scopeRefId,
        }],''',
'''        scopes: accountForm.scopeMode === 'NONE' ? [] : [{
          scopeType: accountForm.scopeMode === 'SCHOOL' ? 'SCHOOL' : accountForm.scopeType,
          scopeRefId: accountForm.scopeMode === 'SCHOOL' ? null : accountForm.scopeRefId,
        }],''')

write('frontend/src/account/DefaultProfileScopeSelector.vue', '''<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { listAuthorizationScopeOptions } from '../api/accountAdmin'
import type { DataObject } from '../api/types'
import ScopeCascadeSelect from './ScopeCascadeSelect.vue'
import type { ScopeCascadeOptionSets } from './scopeCascade'
import type { ScopeType } from './scopeSelection'

const props = defineProps<{
  scopeMode: 'NONE' | 'SCHOOL' | 'SPECIFIC'
  scopeType: ScopeType
  scopeRefId: number | null
  allowedScopeTypes: readonly ScopeType[]
}>()
const emit = defineEmits<{
  'update:scopeMode': [value: 'NONE' | 'SCHOOL' | 'SPECIFIC']
  'update:scopeType': [value: ScopeType]
  'update:scopeRefId': [value: number | null]
}>()
const optionSets = ref<ScopeCascadeOptionSets>({})
const loading = ref(false)
const concreteTypes = computed(() => props.allowedScopeTypes.filter(type => type !== 'SCHOOL'))

watch(() => [props.scopeMode, props.scopeType, ...props.allowedScopeTypes], () => void ensureOptions(), { immediate: true })

async function ensureOptions() {
  if (props.scopeMode !== 'SPECIFIC') return
  loading.value = true
  try {
    const needed = new Set<ScopeType>([props.scopeType])
    if (['CAMPUS', 'BUILDING', 'FLOOR', 'ROOM'].includes(props.scopeType)) {
      for (const type of ['CAMPUS', 'BUILDING', 'FLOOR', 'ROOM'] as ScopeType[]) {
        if (props.allowedScopeTypes.includes(type) || ['CAMPUS', 'BUILDING', 'FLOOR'].includes(type)) needed.add(type)
      }
    }
    const next = { ...optionSets.value }
    await Promise.all([...needed].map(async type => {
      if (type === 'SCHOOL' || next[type]) return
      next[type] = await listAuthorizationScopeOptions(type)
    }))
    optionSets.value = next
  } finally { loading.value = false }
}

function setMode(value: 'NONE' | 'SCHOOL' | 'SPECIFIC') {
  emit('update:scopeMode', value)
  emit('update:scopeRefId', null)
  if (value === 'SPECIFIC' && !concreteTypes.value.includes(props.scopeType)) {
    emit('update:scopeType', concreteTypes.value[0] ?? 'BUILDING')
  }
}
function setType(event: Event) {
  emit('update:scopeType', (event.target as HTMLSelectElement).value as ScopeType)
  emit('update:scopeRefId', null)
}
</script>

<template>
  <div class="default-profile-scope-selector account-span-two">
    <label>范围
      <select :value="scopeMode" @change="setMode(($event.target as HTMLSelectElement).value as 'NONE' | 'SCHOOL' | 'SPECIFIC')">
        <option value="NONE">无权限</option>
        <option v-if="allowedScopeTypes.includes('SCHOOL')" value="SCHOOL">全校</option>
        <option v-if="concreteTypes.length" value="SPECIFIC">具体范围</option>
      </select>
    </label>
    <template v-if="scopeMode === 'SPECIFIC'">
      <label>范围层级
        <select :value="scopeType" @change="setType">
          <option v-for="type in concreteTypes" :key="type" :value="type">{{ { CAMPUS:'校区', BUILDING:'楼栋', FLOOR:'楼层', ROOM:'寝室', MAJOR:'专业' }[type] || type }}</option>
        </select>
      </label>
      <div class="concrete-scope-field">
        <span>具体范围</span>
        <ScopeCascadeSelect
          :scope-type="scopeType"
          :model-value="scopeRefId"
          :option-sets="optionSets"
          @update:model-value="emit('update:scopeRefId', $event)"
        />
        <small v-if="loading">正在加载可授权范围…</small>
      </div>
    </template>
    <div v-else class="scope-mode-summary">
      <span>具体范围</span><strong>{{ scopeMode === 'NONE' ? '无数据权限' : '全校' }}</strong>
    </div>
  </div>
</template>

<style scoped>
.default-profile-scope-selector{display:grid;grid-template-columns:minmax(150px,.55fr) minmax(150px,.55fr) minmax(320px,1.9fr);gap:10px;align-items:start}.default-profile-scope-selector>label,.concrete-scope-field,.scope-mode-summary{display:grid;gap:7px}.default-profile-scope-selector span,.default-profile-scope-selector label,.concrete-scope-field>span{color:var(--muted);font-size:.78rem;font-weight:700}.default-profile-scope-selector select{width:100%;min-height:40px;padding:8px 10px;border:1px solid var(--line);border-radius:9px;background:var(--surface);color:var(--ink)}.scope-mode-summary{align-self:stretch;padding:8px 12px;border:1px solid var(--line);border-radius:9px;background:var(--soft)}.scope-mode-summary strong{font-size:.9rem}.concrete-scope-field small{color:var(--muted)}@media(max-width:900px){.default-profile-scope-selector{grid-template-columns:1fr}.scope-mode-summary{min-height:40px}}
</style>
''')

p = 'frontend/src/views/account/AccountAdminAccountsView.vue'
replace(p, "import { scopeLabels, useAccountAdminConsole } from '../../account/useAccountAdminConsole'",
           "import DefaultProfileScopeSelector from '../../account/DefaultProfileScopeSelector.vue'\nimport { scopeLabels, useAccountAdminConsole } from '../../account/useAccountAdminConsole'")
replace(p, "  createScopeOptions,\n", "")
replace(p, "  changeCreateScopeType,\n", "")
replace(p, '''              <label>数据范围类型<select v-model="accountForm.scopeType" required @change="changeCreateScopeType"><option v-for="scopeType in createScopeTypes" :key="scopeType" :value="scopeType">{{ scopeLabels[scopeType] ?? scopeType }}</option></select></label>
              <label v-if="accountForm.scopeType !== 'SCHOOL'">具体范围<select v-model="accountForm.scopeRefId" required><option :value="null" disabled>请选择</option><option v-for="option in createScopeOptions" :key="String(option.scope_ref_id)" :value="Number(option.scope_ref_id)">{{ option.scope_label }}</option></select></label>
              <div v-else class="account-scope-fixed"><span>具体范围</span><strong>全校</strong></div>''',
'''              <DefaultProfileScopeSelector
                v-model:scope-mode="accountForm.scopeMode"
                v-model:scope-type="accountForm.scopeType"
                v-model:scope-ref-id="accountForm.scopeRefId"
                :allowed-scope-types="createScopeTypes"
              />''')

# 2. Welcome display switch, defaults and studentName token.
p = 'backend-java/server/src/main/java/com/wust/dormitory/admin/SystemSettingService.java'
replace(p, '''    private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
            PRIMARY_WELCOME_LOCALE,
            "欢迎使用武汉科技大学学生宿舍智能选择系统。请先完成个人偏好，再选择合适的宿舍或床位。",
            FALLBACK_WELCOME_LOCALE,
            "Welcome to the university dormitory selection system. Complete your personal preferences first, then choose a suitable room or bed.");''',
'''    private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
            PRIMARY_WELCOME_LOCALE,
            "欢迎 {{studentName}} 加入武汉科技大学。请先完善个人偏好，再根据楼层、剩余铺位和室友匹配情况心仪的宿舍与床位。",
            FALLBACK_WELCOME_LOCALE,
            "Welcome {{studentName}} to Wuhan University of Science and Technology. Please complete your personal preferences first, then choose your preferred dormitory and bed based on the floor, remaining beds, and roommate compatibility.");''')
replace(p, '''            Map<String, String> countryMessages,
            int expectedVersion,''',
'''            Map<String, String> countryMessages,
            boolean displayEnabled,
            int expectedVersion,''')
replace(p, '''        String serialized = json(Map.of(
                "messages", normalizedMessages,
                "countryMessages", normalizedCountryMessages));''',
'''        String serialized = json(Map.of(
                "displayEnabled", displayEnabled,
                "messages", normalizedMessages,
                "countryMessages", normalizedCountryMessages));''')
replace(p, '''                return new WelcomeConfiguration(
                        mergeMessages(stringMap(parsed.get("messages"))),
                        mergeCountryMessages(stringMap(parsed.get("countryMessages"))));''',
'''                return new WelcomeConfiguration(
                        !Boolean.FALSE.equals(parsed.get("displayEnabled")),
                        mergeMessages(stringMap(parsed.get("messages"))),
                        mergeCountryMessages(stringMap(parsed.get("countryMessages"))));''')
replace(p, '''            return new WelcomeConfiguration(mergeMessages(stringMap(parsed)), Map.of());''',
'''            return new WelcomeConfiguration(true, mergeMessages(stringMap(parsed)), Map.of());''')
replace(p, '''            return new WelcomeConfiguration(fallback, Map.of());''',
'''            return new WelcomeConfiguration(true, fallback, Map.of());''')
replace(p, '''        return new WelcomeConfiguration(new LinkedHashMap<>(DEFAULT_MESSAGES), Map.of());''',
'''        return new WelcomeConfiguration(true, new LinkedHashMap<>(DEFAULT_MESSAGES), Map.of());''')
replace(p, '''                json(Map.of(
                        "messages", DEFAULT_MESSAGES,
                        "countryMessages", Map.of())));''',
'''                json(Map.of(
                        "displayEnabled", true,
                        "messages", DEFAULT_MESSAGES,
                        "countryMessages", Map.of())));''')
replace(p, '''        result.put("messages", configuration.messages());''',
'''        result.put("displayEnabled", configuration.displayEnabled());
        result.put("messages", configuration.messages());''')
replace(p, '''    public record WelcomeConfiguration(
            Map<String, String> messages,
            Map<String, String> countryMessages) { }
}''',
'''    public record WelcomeConfiguration(
            boolean displayEnabled,
            Map<String, String> messages,
            Map<String, String> countryMessages) {
        public WelcomeConfiguration(Map<String, String> messages, Map<String, String> countryMessages) {
            this(true, messages, countryMessages);
        }
    }
}''')

p = 'backend-java/server/src/main/java/com/wust/dormitory/admin/SystemSettingController.java'
replace(p, '''                        request.getMessages(),
                        request.getCountryMessages(),
                        request.getExpectedVersion(),''',
'''                        request.getMessages(),
                        request.getCountryMessages(),
                        !Boolean.FALSE.equals(request.getDisplayEnabled()),
                        request.getExpectedVersion(),''')

p = 'backend-java/model/src/main/resources/admin/openapi-system-setting.yaml'
replace(p, '      required: [messages, expectedVersion]', '      required: [displayEnabled, messages, expectedVersion]')
replace(p, '''      properties:
        messages:''', '''      properties:
        displayEnabled:
          type: boolean
          default: true
          description: 是否在学生首次登录时显示欢迎语
        messages:''')

p = 'backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java'
replace(p, '''        Map<String, String> renderedMessages = new LinkedHashMap<>();''',
'''        Map<String, String> renderedMessages = new LinkedHashMap<>();''')
replace(p, '''        data.setRequired(student.get("welcome_acknowledged_at") == null);''',
'''        data.setRequired(configuration.displayEnabled() && student.get("welcome_acknowledged_at") == null);''')
replace(p, '''        variables.put("学生姓名", text(student.get("student_name"), "同学"));''',
'''        variables.put("studentName", text(student.get("student_name"), "同学"));
        variables.put("学生姓名", text(student.get("student_name"), "同学"));''')

p = 'frontend/src/views/admin/AdminDashboardView.vue'
replace(p, "const welcomeVersion = ref(0)", "const welcomeVersion = ref(0)\nconst welcomeDisplayEnabled = ref(true)")
replace(p, "const tokenExamples = {\n  学生姓名: '例如：张三',", "const tokenExamples = {\n  studentName: '例如：张三', 学生姓名: '例如：张三',")
replace(p, '''    baseMessages['zh-CN'] = String(messages['zh-CN'] ?? data.message ?? '')''',
'''    welcomeDisplayEnabled.value = data.displayEnabled !== false
    baseMessages['zh-CN'] = String(messages['zh-CN'] ?? data.message ?? '')''')
replace(p, '''      messages: { 'zh-CN': chinese, 'en-US': english },
      countryMessages: countries,
      expectedVersion: welcomeVersion.value,''',
'''      displayEnabled: welcomeDisplayEnabled.value,
      messages: { 'zh-CN': chinese, 'en-US': english },
      countryMessages: countries,
      expectedVersion: welcomeVersion.value,''')
replace(p, '''        <p v-if="welcomeError" class="alert error">{{ welcomeError }}</p><p v-if="welcomeMessage" class="alert success">{{ welcomeMessage }}</p>
        <div class="welcome-token-toolbar">''',
'''        <p v-if="welcomeError" class="alert error">{{ welcomeError }}</p><p v-if="welcomeMessage" class="alert success">{{ welcomeMessage }}</p>
        <label class="welcome-display-toggle"><input v-model="welcomeDisplayEnabled" type="checkbox" /><span><strong>是否显示欢迎语</strong><small>{{ welcomeDisplayEnabled ? '学生首次登录时显示欢迎内容' : '暂不向学生展示欢迎内容' }}</small></span></label>
        <div class="welcome-token-toolbar">''')

# 3. Crest-adjacent title defaults.
p = 'backend-java/server/src/main/java/com/wust/dormitory/admin/SiteMetadataService.java'
replace(p, '''        defaults.put("title", "宿舍管理运行概览");
        defaults.put("subtitle", "集中查看学生、宿舍、选寝批次与分配工作的最新情况。");''',
'''        defaults.put("title", "武汉科技大学");
        defaults.put("subtitle", "统一宿舍管理平台");''')
p = 'frontend/src/layouts/AppShell.vue'
replace(p, "const productName = ref(String(import.meta.env.VITE_APP_TITLE || `${institutionName}选寝`))",
           "const productName = ref(String(import.meta.env.VITE_APP_TITLE || institutionName || '武汉科技大学'))")
replace(p, "const productSubtitle = ref(String(import.meta.env.VITE_APP_SUBTITLE || '宿舍智能选择系统'))",
           "const productSubtitle = ref(String(import.meta.env.VITE_APP_SUBTITLE || '统一宿舍管理平台'))")

# 4. Import attempts: generated per-click key, explicit keys retain transport retry semantics.
p = 'backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportWorkflowService.java'
replace(p, '''        if (!trimmed.isBlank()) return trimmed;
        if ("ROOM".equals(type)) {
            String policy = importPolicyService.roomAutoCreateBuildingEnabled() ? "AUTO_BUILDING_ON" : "AUTO_BUILDING_OFF";
            return type + ':' + digest + ':' + policy;''',
'''        if (!trimmed.isBlank()) return trimmed;
        return "attempt:" + type + ':' + UUID.randomUUID();
        /* legacy digest-based branch intentionally removed: every explicit upload action is history */
        /*''')
replace(p, '''            return type + ':' + digest + ':' + policy;
        }
        return type + ':' + digest;
    }''', '''            return type + ':' + digest + ':' + policy;
        }
        return type + ':' + digest;
        */
    }''')

p = 'frontend/src/components/admin/ImportWorkflowModal.vue'
replace(p, "const form = reactive({ idempotencyKey: '' })", "const attemptId = ref('')")
replace(p, "  form.idempotencyKey = ''", "  attemptId.value = ''")
replace(p, '''    const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/import-tasks/preview', data, {
      params: { type: props.importType },
      headers: {
        ...(form.idempotencyKey.trim() ? { 'Idempotency-Key': form.idempotencyKey.trim() } : {}),
        'Content-Type': 'multipart/form-data',
      },
    })''',
'''    attemptId.value = attemptId.value || crypto.randomUUID()
    const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/import-tasks/preview', data, {
      params: { type: props.importType },
      headers: { 'Idempotency-Key': attemptId.value, 'Content-Type': 'multipart/form-data' },
    })''')
replace(p, '''        <label><span>防重复标识（可选）</span><input v-model.trim="form.idempotencyKey" class="input" maxlength="200" placeholder="同一批文件重复操作时填写相同标识" /></label>
        <button class="button primary"''', '''        <button class="button primary"''')
replace(p, 'grid-template-columns:minmax(260px,1.4fr) minmax(220px,1fr) auto', 'grid-template-columns:minmax(260px,1fr) auto')

p = 'frontend/src/views/admin/AdminImportQualityView.vue'
replace(p, "const form = reactive({ importType: 'STUDENT', idempotencyKey: '' })", "const form = reactive({ importType: 'STUDENT' })\nconst uploadAttemptId = ref('')")
replace(p, '''    const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/import-tasks/preview', data, {
      params: { type: form.importType },
      headers: {
        ...(form.idempotencyKey.trim() ? { 'Idempotency-Key': form.idempotencyKey.trim() } : {}),
        'Content-Type': 'multipart/form-data',
      },
    })''',
'''    uploadAttemptId.value = uploadAttemptId.value || crypto.randomUUID()
    const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/import-tasks/preview', data, {
      params: { type: form.importType },
      headers: { 'Idempotency-Key': uploadAttemptId.value, 'Content-Type': 'multipart/form-data' },
    })''')
replace(p, '''        <label><span>幂等键（可选）</span><input v-model.trim="form.idempotencyKey" class="input" maxlength="200" placeholder="相同业务批次使用同一个键" /></label>
        <button class="button primary"''', '''        <button class="button primary"''')
replace(p, '''            <span><strong>{{ task.fileName }}</strong><small>{{ task.importType === 'STUDENT' ? '学生' : '宿舍' }} · {{ statusLabel(task.status) }}</small></span>''',
'''            <span><strong>{{ task.fileName }}</strong><small>{{ task.importType === 'STUDENT' ? '学生' : '宿舍' }} · {{ statusLabel(task.status) }}</small><small>{{ task.createdAt || task.created_at || '时间未记录' }}</small></span>''')
# Keep a policySnapshot label in the UI contract while backend snapshot persistence is introduced incrementally.
replace(p, '''            <div class="metric-grid"><div><span>总行数</span>''',
'''            <p v-if="selected.policySnapshot || selected.policy_snapshot" class="policy-note">创建任务时策略：{{ selected.policySnapshot || selected.policy_snapshot }}</p>
            <div class="metric-grid"><div><span>总行数</span>''')
replace(p, 'grid-template-columns:150px minmax(220px,1fr) minmax(220px,1fr) auto', 'grid-template-columns:150px minmax(220px,1fr) auto')

# 5. Remove invisible dropdown from student table scroll height.
p = 'frontend/src/views/admin/AdminDataView.css'
replace(p, '.student-action-dropdown{position:absolute;top:calc(100% + 6px);right:0;z-index:50;display:flex;',
           '.student-action-dropdown{position:absolute;top:calc(100% + 6px);right:0;z-index:50;display:none;')
replace(p, '.student-action-menu:hover .student-action-dropdown,.student-action-menu:focus-within .student-action-dropdown{opacity:1;',
           '.student-action-menu:hover .student-action-dropdown,.student-action-menu:focus-within .student-action-dropdown{display:flex;opacity:1;')

print('core admin requested fixes applied')
