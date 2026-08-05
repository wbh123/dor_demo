#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


def path(name: str) -> Path:
    return ROOT / name


def read(name: str) -> str:
    return path(name).read_text(encoding="utf-8")


def write(name: str, text: str) -> None:
    path(name).write_text(text, encoding="utf-8")


def replace_once(name: str, old: str, new: str) -> None:
    text = read(name)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{name}: expected one exact match, got {count}: {old[:100]!r}")
    write(name, text.replace(old, new, 1))


def sub_once(name: str, pattern: str, replacement: str) -> None:
    text = read(name)
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.DOTALL)
    if count != 1:
        raise RuntimeError(f"{name}: expected one regex match, got {count}: {pattern[:100]!r}")
    write(name, updated)


# 1. 学生列表“修改寝室/床位”：布局坐标属于 room_bed_layout，而不是 bed。
replace_once(
    "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminStudentResidencyAdjustmentService.java",
    """                       bed.position_index, bed.layout_x, bed.layout_z, bed.rotation_degrees,
                       room.id AS room_id, room.room_number, room.room_type,
""",
    """                       bed.position_index, layout.layout_x, layout.layout_z, layout.rotation_degrees,
                       room.id AS room_id, room.room_number, room.room_type,
""",
)
replace_once(
    "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminStudentResidencyAdjustmentService.java",
    """                FROM bed
                JOIN room ON room.id=bed.room_id
""",
    """                FROM bed
                LEFT JOIN room_bed_layout layout ON layout.bed_id=bed.id
                JOIN room ON room.id=bed.room_id
""",
)

# 2. 学生住宿结果优先展示 room_assignment 中经核查后的实际床位，保留原选床事实。
replace_once(
    "backend-java/server/src/main/java/com/wust/dormitory/student/StudentService.java",
    """        List<Map<String, Object>> rows = jdbc.queryForList(\"\"\"
                SELECT a.id, a.assignment_method, a.assigned_at, bed.id AS bed_id,
                       bed.bed_code, bed.bed_type, r.id AS room_id, r.room_number,
                       b.building_name, f.floor_number
                FROM bed_assignment a JOIN bed ON bed.id=a.bed_id
                JOIN room r ON r.id=bed.room_id JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE a.batch_id=:batchId AND a.student_id=:studentId
                \"\"\", new MapSqlParameterSource().addValue(\"batchId\", batchId).addValue(\"studentId\", user.studentId()));
""",
    """        List<Map<String, Object>> rows = jdbc.queryForList(\"\"\"
                SELECT a.id, a.assignment_method, a.assigned_at,
                       COALESCE(actual_bed.id, selected_bed.id) AS bed_id,
                       COALESCE(actual_bed.bed_code, selected_bed.bed_code) AS bed_code,
                       COALESCE(actual_bed.bed_type, selected_bed.bed_type) AS bed_type,
                       r.id AS room_id, r.room_number, b.building_name, f.floor_number
                FROM bed_assignment a
                JOIN bed selected_bed ON selected_bed.id=a.bed_id
                LEFT JOIN room_assignment current_residency
                  ON current_residency.batch_id=a.batch_id
                 AND current_residency.student_id=a.student_id
                 AND current_residency.assignment_status='ACTIVE'
                LEFT JOIN bed actual_bed ON actual_bed.id=current_residency.bed_id
                JOIN room r ON r.id=COALESCE(actual_bed.room_id, selected_bed.room_id)
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE a.batch_id=:batchId AND a.student_id=:studentId
                  AND a.assignment_status='ACTIVE'
                ORDER BY a.assigned_at DESC, a.id DESC
                \"\"\", new MapSqlParameterSource().addValue(\"batchId\", batchId).addValue(\"studentId\", user.studentId()));
""",
)

# 3. 选寝策略采用 update-then-insert，避免依赖 VALUES() 写法；页面保留未展示的第三项策略值。
replace_once(
    "backend-java/server/src/main/java/com/wust/dormitory/selection/SelectionPolicyService.java",
    """    private void upsertPolicySetting(String key, boolean value, long updatedBy) {
        jdbc.update(\"\"\"
                INSERT INTO system_setting(setting_key,setting_value,version,updated_by)
                VALUES (:key,:value,1,:updatedBy)
                ON DUPLICATE KEY UPDATE
                    setting_value=VALUES(setting_value),
                    version=version+1,
                    updated_by=:updatedBy
                \"\"\", new MapSqlParameterSource()
                .addValue(\"key\", key)
                .addValue(\"value\", Boolean.toString(value))
                .addValue(\"updatedBy\", updatedBy));
    }
""",
    """    private void upsertPolicySetting(String key, boolean value, long updatedBy) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(\"key\", key)
                .addValue(\"value\", Boolean.toString(value))
                .addValue(\"updatedBy\", updatedBy);
        int updated = jdbc.update(\"\"\"
                UPDATE system_setting
                SET setting_value=:value, version=version+1, updated_by=:updatedBy
                WHERE setting_key=:key
                \"\"\", parameters);
        if (updated == 0) {
            jdbc.update(\"\"\"
                    INSERT INTO system_setting(setting_key,setting_value,version,updated_by)
                    VALUES (:key,:value,1,:updatedBy)
                    \"\"\", parameters);
        }
    }
""",
)
matching = "frontend/src/views/admin/AdminMatchingView.vue"
replace_once(
    matching,
    "const selectionPolicy = reactive({ allowWithoutQuestionnaire: false, allowStudentReselect: false, questionnaireBypassFeatureEnabled: false, studentReselectFeatureEnabled: false, version: 0, reason: '' })",
    "const selectionPolicy = reactive({ allowWithoutQuestionnaire: false, allowStudentReselect: false, directPreferenceWithoutBatchAllowed: true, questionnaireBypassFeatureEnabled: false, studentReselectFeatureEnabled: false, version: 0, reason: '' })",
)
replace_once(
    matching,
    "    selectionPolicy.allowStudentReselect = Boolean(policy.allowStudentReselect)\n",
    "    selectionPolicy.allowStudentReselect = Boolean(policy.allowStudentReselect)\n    selectionPolicy.directPreferenceWithoutBatchAllowed = Boolean(policy.directPreferenceWithoutBatchAllowed ?? true)\n",
)
replace_once(
    matching,
    "      allowStudentReselect: selectionPolicy.allowStudentReselect,\n      expectedVersion: selectionPolicy.version,",
    "      allowStudentReselect: selectionPolicy.allowStudentReselect,\n      directPreferenceWithoutBatchAllowed: selectionPolicy.directPreferenceWithoutBatchAllowed,\n      expectedVersion: selectionPolicy.version,",
)
replace_once(matching, '<section class="panel weight-manual">', '<section class="panel weight-manual compact-weight-manual">')
replace_once(
    matching,
    ".weight-manual{display:grid;gap:14px}.weight-manual-grid article{padding:14px;border:1px solid var(--border);border-radius:13px}.weight-manual-grid strong{font-size:20px}.weight-manual-grid p{margin:5px 0 0;color:var(--text-muted)}",
    ".weight-manual{display:grid;gap:10px;padding:14px 16px}.compact-weight-manual h3{margin:2px 0 0;font-size:17px}.compact-weight-manual>p{margin:0;color:var(--text-muted);font-size:12px;line-height:1.55}.weight-manual-grid{gap:8px}.weight-manual-grid article{padding:9px 11px;border:1px solid var(--border);border-radius:11px}.weight-manual-grid strong{font-size:15px}.weight-manual-grid p{margin:3px 0 0;color:var(--text-muted);font-size:11px;line-height:1.4}",
)

# 4. 公共遮罩：学生资料修改、住宿调整和两类重置。
admin_data = "frontend/src/views/admin/AdminDataView.vue"
replace_once(admin_data, "import ImportWorkflowModal", "import AppModal from '../../components/modal/AppModal.vue'\nimport ImportWorkflowModal")
sub_once(
    admin_data,
    r'<div v-if="editingStudent" class="modal-overlay student-edit-overlay".*?<section class="modal-card student-dialog".*?>(.*?)</section></div>\n\n    <div v-if="placementTarget"',
    r'''<AppModal :open="Boolean(editingStudent)" size="wide" :busy="savingStudent" @close="() => closeStudentEdit()"><div class="student-dialog">\1</div></AppModal>

    <div v-if="placementTarget"''',
)
sub_once(
    admin_data,
    r'<div v-if="placementTarget" class="modal-overlay placement-overlay".*?<section class="modal-card placement-dialog">(.*?)</section></div>\n\n    <div v-if="resetTarget"',
    r'''<AppModal :open="Boolean(placementTarget)" size="large" :busy="placementSaving" @close="closePlacement"><div class="placement-dialog">\1</div></AppModal>

    <div v-if="resetTarget"''',
)
sub_once(
    admin_data,
    r'<div v-if="resetTarget" class="modal-overlay student-reset-overlay".*?<section class="modal-card reset-dialog">(.*?)</section></div>',
    r'''<AppModal :open="Boolean(resetTarget)" size="default" :busy="resetting" @close="closeReset"><div class="reset-dialog">\1</div></AppModal>''',
)

# 5. 偏好检查、申请换入、在住调整床位改用公共 AppModal。
room_list = "frontend/src/views/student/RoomListView.vue"
replace_once(room_list, "import { api }", "import AppModal from '../../components/modal/AppModal.vue'\nimport { api }")
replace_once(
    room_list,
    '''    <div v-if="preferencePromptVisible" class="modal-overlay" @click.self="preferencePromptVisible = false"><section class="modal-card preference-warning-dialog" role="dialog" aria-modal="true"><span class="eyebrow">偏好检查</span><h3>尚未填写个人偏好</h3><p>填写偏好后才能获得更准确的室友匹配、冲突提醒和床位类型提示。</p><div class="button-row"><button class="button secondary" @click="router.push('/student/preferences')">先填写偏好</button><button v-if="selectionReadiness.allowWithoutQuestionnaire" class="button primary" @click="continueWithoutPreference">仍然继续选寝</button></div></section></div>''',
    '''    <AppModal :open="preferencePromptVisible" title="尚未填写个人偏好" description="填写偏好后才能获得更准确的室友匹配、冲突提醒和床位类型提示。" size="compact" @close="preferencePromptVisible = false"><div class="preference-warning-dialog"><span class="eyebrow">偏好检查</span></div><template #footer><button class="button secondary" @click="router.push('/student/preferences')">先填写偏好</button><button v-if="selectionReadiness.allowWithoutQuestionnaire" class="button primary" @click="continueWithoutPreference">仍然继续选寝</button></template></AppModal>''',
)

room_change = "frontend/src/views/student/StudentRoomChangeView.vue"
replace_once(room_change, "import { api }", "import AppModal from '../../components/modal/AppModal.vue'\nimport { api }")
replace_once(
    room_change,
    '''    <div v-if="target" class="modal-overlay" @click.self="target=null"><section class="modal-card dialog"><h3>申请换入 {{ target.building_name }} {{ target.room_number }}室</h3><form class="form-stack" @submit.prevent="submitRoomChange"><textarea v-model.trim="form.reason" class="input" required maxlength="500" rows="4" placeholder="请填写换寝原因"/><div class="button-row"><button type="button" class="button ghost" @click="target=null">取消</button><button class="button primary" :disabled="submitting">提交</button></div></form></section></div>''',
    '''    <AppModal :open="Boolean(target)" :title="target ? `申请换入 ${target.building_name} ${target.room_number}室` : '申请换入'" size="default" :busy="submitting" @close="target=null"><form class="form-stack dialog" @submit.prevent="submitRoomChange"><textarea v-model.trim="form.reason" class="input" required maxlength="500" rows="4" placeholder="请填写换寝原因"/></form><template #footer><button type="button" class="button ghost" @click="target=null">取消</button><button class="button primary" :disabled="submitting || !form.reason.trim()" @click="submitRoomChange">提交</button></template></AppModal>''',
)

residency = "frontend/src/views/admin/AdminResidencyView.vue"
replace_once(residency, "import { api }", "import AppModal from '../../components/modal/AppModal.vue'\nimport { api }")
sub_once(
    residency,
    r'<div v-if="selected" class="modal-overlay".*?<section class="modal-card bed-confirm-dialog">(.*?)</section>\n    </div>',
    r'''<AppModal :open="Boolean(selected)" size="wide" :busy="saving" @close="closeDialog"><div class="bed-confirm-dialog">\1</div></AppModal>''',
)

# 6. 首次欢迎：图标与大标题同一行、左对齐，正文在下方。
shell = "frontend/src/layouts/AppShell.vue"
sub_once(
    shell,
    r'''    <AppModal\n      :open="auth\.welcomeRequired".*?</AppModal>''',
    '''    <AppModal
      :open="auth.welcomeRequired"
      :close-on-backdrop="false"
      :close-on-escape="false"
      prevent-close
      size="default"
    >
      <template #header>
        <div class="welcome-modal-heading-row">
          <img class="welcome-school-logo logo-safe-layer" :src="logoOnly" alt="" aria-hidden="true" />
          <div><span class="eyebrow">{{ subtitle('欢迎来到校园', 'WELCOME TO CAMPUS') }}</span><h2>{{ t('welcome.title') }}</h2></div>
        </div>
      </template>
      <div class="welcome-modal-content">
        <p class="welcome-modal-message">{{ welcomeText }}</p>
        <p v-if="welcomeError" class="alert error">{{ welcomeError }}</p>
      </div>
      <template #footer>
        <button class="button primary welcome-start-button" :disabled="auth.welcomeAcknowledging" @click="acknowledgeWelcome">{{ auth.welcomeAcknowledging ? '正在进入…' : t('welcome.start') }}</button>
      </template>
    </AppModal>''',
)
replace_once(
    shell,
    "<style scoped>\n",
    "<style scoped>\n.welcome-modal-heading-row{display:flex;align-items:center;justify-content:flex-start;gap:14px;width:100%;text-align:left}.welcome-modal-heading-row h2{margin:4px 0 0;font-size:26px;line-height:1.2}.welcome-modal-heading-row .welcome-school-logo{width:52px;height:52px;object-fit:contain;flex:0 0 auto}.welcome-modal-content{text-align:left}.welcome-modal-message{margin:0;line-height:1.75;color:var(--text-muted)}\n",
)

# 7. 报表 v-model 警告、高级审计详情和历史分析摘要。
governance = "frontend/src/views/admin/AdminGovernanceView.vue"
replace_once(governance, "const auditRows = ref<DataObject[]>([])\n", "const auditRows = ref<DataObject[]>([])\nconst selectedAudit = ref<DataObject | null>(null)\n")
replace_once(
    governance,
    "  await Promise.allSettled(requests)\n})",
    "  if (canAuditQuery.value) requests.push(queryAudit())\n  await Promise.allSettled(requests)\n})",
)
replace_once(
    governance,
    "function resetAnalytics(){Object.assign(analyticsFilters,{academicYear:'',batchId:'',majorId:'',gradeYear:'',degreeLevel:'',studentCategory:'',campusId:'',buildingId:'',roomType:''})}\n",
    "function resetAnalytics(){Object.assign(analyticsFilters,{academicYear:'',batchId:'',majorId:'',gradeYear:'',degreeLevel:'',studentCategory:'',campusId:'',buildingId:'',roomType:''})}\nfunction updateReportDefinition(value: typeof reportDefinition){Object.assign(reportDefinition, value)}\nfunction auditJson(value:unknown){if(value==null||value==='')return '无';try{return JSON.stringify(typeof value==='string'?JSON.parse(value):value,null,2)}catch{return String(value)}}\n",
)
replace_once(
    governance,
    '<label><span>保存或生成原因</span><textarea v-model="reportReason" class="input" rows="3" /></label><ReportBuilder v-model="reportDefinition"',
    '<label><span>保存或生成原因</span><textarea v-model="reportReason" class="input" rows="3" /></label><ReportBuilder :model-value="reportDefinition" @update:model-value="updateReportDefinition"',
)
replace_once(
    governance,
    '<div v-if="canAuditQuery" class="table-wrap"><table><thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>目标</th><th>结果</th><th>请求编号</th><th>网络地址</th></tr></thead><tbody><tr v-for="row in auditRows" :key="String(row.id)"><td>{{ row.occurred_at }}</td><td>{{ row.operator_type }} #{{ row.operator_user_id }}</td><td>{{ row.action_type }}</td><td>{{ row.resource_type }} {{ row.resource_id }}</td><td>{{ row.result_status }}</td><td>{{ row.request_id }}</td><td>{{ row.network_address }}</td></tr></tbody></table></div>',
    '<div v-if="canAuditQuery" class="table-wrap"><table><thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>目标</th><th>结果</th><th>请求编号</th><th>网络地址</th><th>详情</th></tr></thead><tbody><tr v-for="row in auditRows" :key="String(row.id)"><td>{{ row.occurred_at }}</td><td>{{ row.operator_type }} #{{ row.operator_user_id }}</td><td>{{ row.action_type }}</td><td>{{ row.resource_type }} {{ row.resource_id }}</td><td>{{ row.result_status }}</td><td>{{ row.request_id }}</td><td>{{ row.network_address }}</td><td><button class="button ghost small" type="button" @click="selectedAudit=row">查看</button></td></tr></tbody></table></div><article v-if="selectedAudit" class="audit-detail-card"><div class="section-head"><div><span class="eyebrow">AUDIT DETAIL</span><h4>{{ selectedAudit.action_type }}</h4><p>{{ selectedAudit.module || selectedAudit.resource_type }} · {{ selectedAudit.occurred_at }}</p></div><button class="button ghost small" type="button" @click="selectedAudit=null">关闭</button></div><div class="audit-detail-grid"><div><span>结果</span><strong>{{ selectedAudit.result_status }}</strong></div><div><span>错误代码</span><strong>{{ selectedAudit.error_code || '无' }}</strong></div><div><span>原因</span><strong>{{ selectedAudit.reason || '未填写' }}</strong></div><div><span>请求编号</span><strong>{{ selectedAudit.request_id || '无' }}</strong></div></div><div class="audit-json-grid"><div><strong>变更前</strong><pre>{{ auditJson(selectedAudit.before_data) }}</pre></div><div><strong>变更后</strong><pre>{{ auditJson(selectedAudit.after_data) }}</pre></div></div></article>',
)
replace_once(
    governance,
    '<p v-if="analyticsPrivacy.preferenceDimensionsSuppressed" class="alert warning">',
    '<div class="analytics-summary-grid"><article><span>结果批次</span><strong>{{ analyticsItems.length }}</strong></article><article><span>统计口径</span><strong>{{ analyticsItems[0]?.metric_version || \'待查询\' }}</strong></article><article><span>隐私阈值</span><strong>{{ analyticsPrivacy.privacyThreshold || \'—\' }}</strong></article></div><p v-if="analyticsPrivacy.preferenceDimensionsSuppressed" class="alert warning">',
)
replace_once(
    governance,
    ".governance-page{gap:18px}",
    ".governance-page{gap:18px}.audit-detail-card{display:grid;gap:14px;margin-top:14px;padding:16px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}.audit-detail-grid,.analytics-summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.audit-detail-grid div,.analytics-summary-grid article{display:grid;gap:5px;padding:11px;border-radius:11px;background:var(--surface)}.audit-detail-grid span,.analytics-summary-grid span{color:var(--text-muted);font-size:12px}.audit-json-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.audit-json-grid pre{max-height:240px;overflow:auto;white-space:pre-wrap}.analytics-summary-grid{grid-template-columns:repeat(3,minmax(0,1fr));margin:12px 0}@media(max-width:760px){.audit-detail-grid,.analytics-summary-grid,.audit-json-grid{grid-template-columns:1fr}}",
)

# 8. 首页业务操作记录补充动作、目标、结果和时间。
dashboard = "frontend/src/views/admin/AdminDashboardView.vue"
replace_once(
    dashboard,
    "function batchStatus(value: unknown) {\n",
    """function auditActionText(value: unknown) {
  const key = String(value ?? '')
  return ({ STUDENT_CREATE:'录入学生', STUDENT_UPDATE:'修改学生资料', STUDENT_PASSWORD_RESET:'重置学生密码', STUDENT_STATE_RESET:'完全重置学生状态', RESIDENCY_ASSIGN:'分配寝室床位', RESIDENCY_ADJUST:'调整寝室床位', SELECTION_POLICY_UPDATE:'修改选寝策略', BED_CONFIRMATION_ROOM_APPROVE:'审批实际床位', BATCH_PUBLISH:'发布选寝批次' } as Record<string,string>)[key] ?? key.replaceAll('_',' ').toLowerCase() || '完成业务操作'
}
function auditTargetText(log: DataObject) {
  const type = String(log.resource_type ?? log.target_type ?? '业务对象')
  const id = log.resource_id ?? log.target_id
  return id == null || id === '' ? type : `${type} #${id}`
}
function auditResultText(value: unknown) { return String(value) === 'SUCCESS' ? '成功' : String(value) === 'FAILED' ? '失败' : String(value ?? '已记录') }
function formatAuditTime(value: unknown) { const date = new Date(String(value ?? '')); return Number.isNaN(date.getTime()) ? '时间未记录' : date.toLocaleString() }

function batchStatus(value: unknown) {
""",
)
replace_once(
    dashboard,
    '<div class="compact-list"><article v-for="log in auditLogs" :key="String(log.id)"><strong>{{ log.operator_name || \'系统\' }}</strong><span>{{ log.action || log.operation_type || \'完成业务操作\' }}</span></article><p v-if="!auditLogs.length" class="empty-state">暂无操作记录。</p></div>',
    '<div class="compact-list operation-record-list"><article v-for="log in auditLogs" :key="String(log.id)"><div><strong>{{ auditActionText(log.action_type || log.action || log.operation_type) }}</strong><small>{{ log.operator_name || log.operator_type || \'系统\' }} · {{ auditTargetText(log) }}</small></div><div class="operation-record-meta"><span :class="{ failed:String(log.result_status)===\'FAILED\' }">{{ auditResultText(log.result_status) }}</span><small>{{ formatAuditTime(log.occurred_at || log.created_at) }}</small></div></article><p v-if="!auditLogs.length" class="empty-state">暂无操作记录。</p></div>',
)
replace_once(
    dashboard,
    ".compact-list span{color:var(--muted)}",
    ".compact-list span{color:var(--muted)}.operation-record-list article{align-items:flex-start}.operation-record-list article>div{display:grid;gap:4px}.operation-record-list small{color:var(--muted);font-size:11px}.operation-record-meta{text-align:right}.operation-record-meta span{font-size:12px;font-weight:700;color:#17664f}.operation-record-meta span.failed{color:#b42318}",
)

print("requested residency/governance fixes applied")
