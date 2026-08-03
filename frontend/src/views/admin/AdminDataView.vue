<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import TransferStudentWizard from '../../components/admin/TransferStudentWizard.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, MajorRequest, ObjectSuccessResponse } from '../../api/types'
import { countryLabel, countryOptions } from '../../utils/countries'
import { useI18n } from '../../i18n'

interface StudentForm {
  studentNumber: string
  studentName: string
  gender: 'M' | 'F'
  majorId: number
  nationalityCode: string
  studentCategory: 'DOMESTIC' | 'INTERNATIONAL'
  enrollmentSource: 'ADMIN_MANUAL' | 'TRANSFER_MANUAL' | 'INITIAL_IMPORT' | 'BATCH_IMPORT'
  phoneNumber: string
  degreeLevel: '' | 'UNDERGRADUATE' | 'MASTER' | 'DOCTOR' | 'MASTER_DOCTOR'
  gradeYear: number | null
}

const majors = ref<DataObject[]>([])
const students = ref<DataObject[]>([])
const total = ref(0)
const keyword = ref('')
const categoryFilter = ref('')
const sourceFilter = ref('')
const error = ref('')
const message = ref('')
const editingStudentId = ref<number | null>(null)
const transferVisible = ref(false)
const resetting = ref(false)
const resetTarget = ref<DataObject | null>(null)
const resetMode = ref<'password' | 'state'>('password')
const importing = ref(false)
const selectableCountries = computed(() => studentForm.studentCategory === 'DOMESTIC' ? countryOptions.filter((country) => country.code === 'CN') : countryOptions.filter((country) => country.code !== 'CN'))
const importFile = ref<File | null>(null)
const resetForm = reactive({ reason: '', confirmStudentNumber: '' })
const majorForm = reactive<MajorRequest>({ majorCode: '', majorName: '', enabled: true })
const studentForm = reactive<StudentForm>({ studentNumber: '', studentName: '', gender: 'M', majorId: 0, nationalityCode: 'CN', studentCategory: 'DOMESTIC', enrollmentSource: 'ADMIN_MANUAL', phoneNumber: '', degreeLevel: '', gradeYear: null })
const { subtitle, translateError } = useI18n()

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [majorResponse, studentResponse] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/admin/majors'),
      api.get<ObjectSuccessResponse>('/api/v1/admin/students', { params: { keyword: keyword.value || undefined, studentCategory: categoryFilter.value || undefined, enrollmentSource: sourceFilter.value || undefined, page: 1, size: 100 } }),
    ])
    majors.value = (majorResponse.data.data ?? []) as DataObject[]
    const studentData = (studentResponse.data.data ?? {}) as DataObject
    students.value = (studentData.items ?? []) as DataObject[]
    total.value = Number(studentData.total ?? 0)
    if (!studentForm.majorId && majors.value.length) studentForm.majorId = Number(majors.value[0].id)
  } catch (reason) { error.value = translateError(reason) }
}

async function createMajor() {
  try { await api.post('/api/v1/admin/majors', majorForm); majorForm.majorCode = ''; majorForm.majorName = ''; majorForm.enabled = true; message.value = '专业已创建。'; await load() }
  catch (reason) { error.value = translateError(reason) }
}
async function toggleMajor(major: DataObject) {
  try { await api.put(`/api/v1/admin/majors/${major.id}`, { majorCode: major.major_code, majorName: major.major_name, enabled: !Boolean(major.enabled) }); await load() }
  catch (reason) { error.value = translateError(reason) }
}
function setStudentCategory(category: StudentForm['studentCategory']) {
  studentForm.studentCategory = category
  if (category === 'DOMESTIC') studentForm.nationalityCode = 'CN'
  else if (studentForm.nationalityCode === 'CN') studentForm.nationalityCode = 'US'
}
async function saveStudent() {
  error.value = ''; message.value = ''
  try {
    const payload = { ...studentForm, nationalityCode: String(studentForm.nationalityCode || 'CN').trim().toUpperCase(), phoneNumber: String(studentForm.phoneNumber || '').trim() || undefined, degreeLevel: studentForm.degreeLevel || undefined, gradeYear: studentForm.gradeYear || undefined }
    if (editingStudentId.value) { await api.put(`/api/v1/admin/students/${editingStudentId.value}`, payload); message.value = '学生资料已更新。' }
    else { await api.post('/api/v1/admin/students', payload); message.value = '学生已创建，账号处于待激活状态。' }
    resetStudentForm(); await load()
  } catch (reason) { error.value = translateError(reason) }
}
function editStudent(student: DataObject) {
  editingStudentId.value = Number(student.id); studentForm.studentNumber = String(student.student_number); studentForm.studentName = String(student.student_name); studentForm.gender = String(student.gender) as StudentForm['gender']; studentForm.majorId = Number(student.major_id); studentForm.nationalityCode = String(student.nationality_code ?? 'CN'); studentForm.studentCategory = String(student.student_category ?? 'DOMESTIC') as StudentForm['studentCategory']; studentForm.enrollmentSource = String(student.enrollment_source ?? 'ADMIN_MANUAL') as StudentForm['enrollmentSource']; studentForm.phoneNumber = String(student.phone_number ?? ''); studentForm.degreeLevel = String(student.degree_level ?? '') as StudentForm['degreeLevel']; studentForm.gradeYear = student.grade_year ? Number(student.grade_year) : null; window.scrollTo({ top: 0, behavior: 'smooth' })
}
function resetStudentForm() {
  editingStudentId.value = null; studentForm.studentNumber = ''; studentForm.studentName = ''; studentForm.gender = 'M'; studentForm.nationalityCode = 'CN'; studentForm.studentCategory = 'DOMESTIC'; studentForm.enrollmentSource = 'ADMIN_MANUAL'; studentForm.phoneNumber = ''; studentForm.degreeLevel = ''; studentForm.gradeYear = null; if (majors.value.length) studentForm.majorId = Number(majors.value[0].id)
}
function chooseImportFile(event: Event) { importFile.value = (event.target as HTMLInputElement).files?.[0] ?? null }
async function importStudents() {
  if (!importFile.value || importing.value) return
  importing.value = true; error.value = ''; message.value = ''
  try {
    const form = new FormData(); form.append('file', importFile.value)
    const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/import/students', form, { headers: { 'Content-Type': 'multipart/form-data' } })
    const result = (response.data.data ?? {}) as DataObject
    message.value = `导入完成：成功${Number(result.successCount ?? result.success ?? 0)}条，跳过${Number(result.skippedCount ?? result.failed ?? 0)}条。`
    importFile.value = null; await load()
  } catch (reason) { error.value = translateError(reason) }
  finally { importing.value = false }
}
async function downloadStudentTemplate(format: 'xlsx' | 'csv') {
  try {
    const response = await api.get('/api/v1/admin/import/students/template', { params: { format }, responseType: 'blob' })
    const url = URL.createObjectURL(response.data); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `学生导入模板.${format}`; anchor.click(); URL.revokeObjectURL(url)
  } catch (reason) { error.value = translateError(reason) }
}
function openReset(student: DataObject, mode: 'password' | 'state') { resetTarget.value = student; resetMode.value = mode; resetForm.reason = ''; resetForm.confirmStudentNumber = ''; error.value = ''; message.value = '' }
function closeReset() { if (!resetting.value) resetTarget.value = null }
async function submitReset() {
  if (!resetTarget.value) return
  error.value = ''; message.value = ''; resetting.value = true
  try {
    const id = Number(resetTarget.value.id)
    if (resetMode.value === 'password') { await api.post(`/api/v1/admin/students/${id}/reset-password`, { reason: resetForm.reason.trim() }); message.value = `${resetTarget.value.student_name}的密码已重置，账号恢复为待激活。` }
    else { await api.post(`/api/v1/admin/students/${id}/reset-state`, { confirmStudentNumber: resetForm.confirmStudentNumber.trim(), reason: resetForm.reason.trim() }); message.value = `${resetTarget.value.student_name}的账号及全部选寝状态已完全重置。` }
    resetTarget.value = null; await load()
  } catch (reason) { error.value = translateError(reason) }
  finally { resetting.value = false }
}
async function transferCompleted(text: string) { message.value = text; await load() }
function categoryText(value: unknown) { return String(value) === 'INTERNATIONAL' ? '国际生' : '国内生' }
function degreeText(value: unknown) { return ({ UNDERGRADUATE:'本科生', MASTER:'硕士生', DOCTOR:'博士生', MASTER_DOCTOR:'硕博生' } as Record<string,string>)[String(value)] ?? '未填写' }
function sourceText(value: unknown) { return ({ INITIAL_IMPORT:'初始名单', TRANSFER_MANUAL:'转学生补录', ADMIN_MANUAL:'管理员录入', BATCH_IMPORT:'批量导入' } as Record<string,string>)[String(value)] ?? String(value ?? '-') }
</script>

<template>
  <div class="content-column">
    <div class="page-title data-title"><div><span class="eyebrow">{{ subtitle('基础数据', 'MASTER DATA') }}</span><h2>专业与学生</h2><p>维护学生身份、培养层次、年级、国籍和联系方式；支持 Excel 或 CSV 批量导入。</p></div><div class="button-row wrap"><button class="button secondary" @click="downloadStudentTemplate('xlsx')">下载Excel模板</button><button class="button ghost" @click="downloadStudentTemplate('csv')">下载CSV模板</button><button class="button primary" type="button" @click="transferVisible = true">录入转学生</button></div></div>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p>
    <TransferStudentWizard v-model="transferVisible" :majors="majors" @completed="transferCompleted" />

    <section class="panel import-panel"><div><span class="eyebrow">BATCH IMPORT</span><h3>批量导入学生</h3><p>支持 .xlsx、.xls 和 .csv。请先下载模板，专业使用专业编号，国家或地区填写中文或英文名称。</p></div><div class="import-actions"><input class="input" type="file" accept=".xlsx,.xls,.csv,text/csv" @change="chooseImportFile" /><button class="button primary" :disabled="!importFile || importing" @click="importStudents">{{ importing ? '正在导入…' : '导入学生' }}</button></div></section>

    <div v-if="resetTarget" class="modal-overlay student-reset-overlay" @click.self="closeReset"><section class="modal-card student-reset-dialog" role="dialog" aria-modal="true"><header class="section-head split-title"><div><span class="eyebrow">STUDENT ACCOUNT RESET</span><h3>{{ resetMode === 'password' ? '重置学生密码' : '完全重置学生状态' }}</h3><p>{{ resetTarget.student_name }} · {{ resetTarget.student_number }}</p></div><button class="button ghost small" :disabled="resetting" @click="closeReset">关闭</button></header><div class="student-reset-warning" :class="{ danger: resetMode === 'state' }"><strong>{{ resetMode === 'password' ? '仅重置登录信息' : '不可恢复的完整重置' }}</strong><p v-if="resetMode === 'password'">清除密码和登录令牌，不影响在住记录、个人偏好、组队及批次资格。</p><p v-else>清除密码、分配结果、在住记录、个人偏好、组队关系、通知和批次资格，使学生恢复到待激活状态。</p></div><form class="form-stack" @submit.prevent="submitReset"><label v-if="resetMode === 'state'"><span>输入学号确认</span><input v-model.trim="resetForm.confirmStudentNumber" class="input" required pattern="\d{12}" maxlength="12" :placeholder="String(resetTarget.student_number)" /></label><label><span>操作原因</span><textarea v-model.trim="resetForm.reason" class="input" required maxlength="500" rows="3"></textarea></label><div class="button-row"><button class="button ghost" type="button" @click="closeReset">取消</button><button class="button" :class="resetMode === 'state' ? 'danger' : 'primary'" :disabled="resetting">确认</button></div></form></section></div>

    <div class="admin-grid equal">
      <section class="panel"><div class="section-head"><div><span class="eyebrow">MAJORS</span><h3>专业目录</h3></div></div><form class="form-grid" @submit.prevent="createMajor"><input v-model.trim="majorForm.majorCode" class="input" required maxlength="32" placeholder="专业编号" /><input v-model.trim="majorForm.majorName" class="input" required maxlength="128" placeholder="专业名称" /><button class="button primary">新增专业</button></form><div class="simple-list"><article v-for="major in majors" :key="String(major.id)"><div><strong>{{ major.major_name }}</strong><p>{{ major.major_code }}</p></div><button class="button ghost small" @click="toggleMajor(major)">{{ major.enabled ? '禁用' : '启用' }}</button></article></div></section>
      <section class="panel"><div class="section-head split-title"><div><span class="eyebrow">{{ editingStudentId ? 'EDIT STUDENT' : 'NEW STUDENT' }}</span><h3>{{ editingStudentId ? '编辑学生资料' : '普通学生录入' }}</h3></div><button v-if="editingStudentId" class="button ghost small" @click="resetStudentForm">取消编辑</button></div><form class="form-grid student-admin-form" @submit.prevent="saveStudent"><input v-model.trim="studentForm.studentNumber" class="input" required pattern="\d{12}" maxlength="12" placeholder="12位学号" /><input v-model.trim="studentForm.studentName" class="input" required maxlength="128" placeholder="姓名" /><select v-model="studentForm.gender" class="input" required><option value="M">男生</option><option value="F">女生</option></select><select v-model.number="studentForm.majorId" class="input" required><option v-for="major in majors.filter((item) => item.enabled)" :key="String(major.id)" :value="Number(major.id)">{{ major.major_code }} · {{ major.major_name }}</option></select><select v-model="studentForm.degreeLevel" class="input"><option value="">培养层次可留空</option><option value="UNDERGRADUATE">本科生</option><option value="MASTER">硕士生</option><option value="DOCTOR">博士生</option><option value="MASTER_DOCTOR">硕博生</option></select><input v-model.number="studentForm.gradeYear" class="input" type="number" min="2000" max="2100" placeholder="年级，可留空，如2026" /><select v-model="studentForm.nationalityCode" class="input" :disabled="studentForm.studentCategory === 'DOMESTIC'"><option v-for="country in selectableCountries" :key="country.code" :value="country.code">{{ country.name }}</option></select><input v-model.trim="studentForm.phoneNumber" class="input" maxlength="32" placeholder="手机号码，可留空" /><div class="segmented-control span-2"><button type="button" :class="{ active: studentForm.studentCategory === 'DOMESTIC' }" @click="setStudentCategory('DOMESTIC')">国内生</button><button type="button" :class="{ active: studentForm.studentCategory === 'INTERNATIONAL' }" @click="setStudentCategory('INTERNATIONAL')">国际生</button></div><button class="button primary span-2">{{ editingStudentId ? '保存学生资料' : '新增学生' }}</button></form></section>
    </div>

    <section class="panel"><div class="section-head split-title"><div><span class="eyebrow">STUDENTS</span><h3>学生列表 · {{ total }}人</h3></div><form class="student-filter" @submit.prevent="load"><input v-model.trim="keyword" class="input" placeholder="学号、姓名或手机号" /><select v-model="categoryFilter" class="input"><option value="">全部类别</option><option value="DOMESTIC">国内生</option><option value="INTERNATIONAL">国际生</option></select><select v-model="sourceFilter" class="input"><option value="">全部来源</option><option value="INITIAL_IMPORT">初始名单</option><option value="TRANSFER_MANUAL">转学生补录</option><option value="ADMIN_MANUAL">管理员录入</option><option value="BATCH_IMPORT">批量导入</option></select><button class="button secondary">查询</button></form></div><div class="table-wrap"><table><thead><tr><th>学号</th><th>姓名</th><th>性别</th><th>类别</th><th>国家/地区</th><th>培养层次/年级</th><th>专业</th><th>来源</th><th>在住</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="student in students" :key="String(student.id)"><td>{{ student.student_number }}</td><td><strong>{{ student.student_name }}</strong></td><td>{{ student.gender === 'M' ? '男' : '女' }}</td><td><span class="status-chip compact">{{ categoryText(student.student_category) }}</span></td><td>{{ countryLabel(student.nationality_code) }}</td><td>{{ degreeText(student.degree_level) }}<span v-if="student.grade_year"> · {{ student.grade_year }}级</span></td><td>{{ student.major_code }} · {{ student.major_name }}</td><td>{{ sourceText(student.enrollment_source) }}</td><td>{{ student.currently_resident ? '已入住' : '未入住' }}</td><td><span class="status-chip compact">{{ student.account_status }}</span></td><td><div class="button-row wrap student-row-actions"><button class="button ghost small" @click="editStudent(student)">编辑</button><button class="button secondary small" @click="openReset(student, 'password')">重置密码</button><button class="button danger small" @click="openReset(student, 'state')">完全重置</button></div></td></tr></tbody></table></div></section>
  </div>
</template>

<style scoped>
.data-title,.import-panel,.import-actions{display:flex;justify-content:space-between;align-items:flex-start;gap:18px}.import-panel{align-items:center}.import-actions{align-items:center;flex-wrap:wrap}.segmented-control{display:inline-flex;padding:4px;border:1px solid var(--border);border-radius:12px;background:var(--surface-soft)}.segmented-control button{flex:1;border:0;border-radius:9px;padding:9px 16px;background:transparent;color:var(--text-muted)}.segmented-control button.active{background:var(--primary);color:white}.student-filter{display:grid;grid-template-columns:minmax(200px,1fr) 140px 150px auto;gap:8px}@media(max-width:900px){.data-title,.import-panel{flex-direction:column}.student-filter{grid-template-columns:1fr 1fr}}
</style>
