<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
  phoneNumber: string
  degreeLevel: '' | 'UNDERGRADUATE' | 'MASTER' | 'DOCTOR' | 'MASTER_DOCTOR'
  gradeYear: number | null
}

const majors = ref<DataObject[]>([])
const students = ref<DataObject[]>([])
const total = ref(0)
const keyword = ref('')
const categoryFilter = ref('')
const error = ref('')
const message = ref('')
const editingStudentId = ref<number | null>(null)
const editingStudent = ref<DataObject | null>(null)
const savingStudent = ref(false)
const resetting = ref(false)
const resetTarget = ref<DataObject | null>(null)
const resetMode = ref<'password' | 'state'>('password')
const importing = ref(false)
const importFile = ref<File | null>(null)
const resetForm = reactive({ reason: '', confirmStudentNumber: '' })
const majorForm = reactive<MajorRequest>({ majorCode: '', majorName: '', enabled: true })
const studentForm = reactive<StudentForm>({
  studentNumber: '', studentName: '', gender: 'M', majorId: 0,
  nationalityCode: 'CN', studentCategory: 'DOMESTIC', phoneNumber: '',
  degreeLevel: '', gradeYear: null,
})
const selectableCountries = computed(() => studentForm.studentCategory === 'DOMESTIC'
  ? countryOptions.filter((country) => country.code === 'CN')
  : countryOptions.filter((country) => country.code !== 'CN'))
const { subtitle, translateError } = useI18n()

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [majorResponse, studentResponse] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/admin/majors'),
      api.get<ObjectSuccessResponse>('/api/v1/admin/students', {
        params: {
          keyword: keyword.value || undefined,
          studentCategory: categoryFilter.value || undefined,
          page: 1,
          size: 100,
        },
      }),
    ])
    majors.value = (majorResponse.data.data ?? []) as DataObject[]
    const studentData = (studentResponse.data.data ?? {}) as DataObject
    students.value = (studentData.items ?? []) as DataObject[]
    total.value = Number(studentData.total ?? 0)
    if (!studentForm.majorId && majors.value.length) studentForm.majorId = Number(majors.value[0].id)
  } catch (reason) {
    error.value = translateError(reason)
  }
}

async function createMajor() {
  error.value = ''; message.value = ''
  try {
    await api.post('/api/v1/admin/majors', majorForm)
    majorForm.majorCode = ''; majorForm.majorName = ''; majorForm.enabled = true
    message.value = '专业已创建。'
    await load()
  } catch (reason) { error.value = translateError(reason) }
}

async function toggleMajor(major: DataObject) {
  try {
    await api.put(`/api/v1/admin/majors/${major.id}`, {
      majorCode: major.major_code,
      majorName: major.major_name,
      enabled: !Boolean(major.enabled),
    })
    await load()
  } catch (reason) { error.value = translateError(reason) }
}

function setStudentCategory(category: StudentForm['studentCategory']) {
  studentForm.studentCategory = category
  if (category === 'DOMESTIC') studentForm.nationalityCode = 'CN'
  else if (studentForm.nationalityCode === 'CN') studentForm.nationalityCode = 'US'
}

function studentPayload() {
  return {
    ...studentForm,
    enrollmentSource: 'ADMIN_MANUAL',
    nationalityCode: String(studentForm.nationalityCode || 'CN').trim().toUpperCase(),
    phoneNumber: String(studentForm.phoneNumber || '').trim() || undefined,
    degreeLevel: studentForm.degreeLevel || undefined,
    gradeYear: studentForm.gradeYear || undefined,
  }
}

async function saveStudent() {
  if (savingStudent.value) return
  savingStudent.value = true; error.value = ''; message.value = ''
  try {
    if (editingStudentId.value) {
      await api.put(`/api/v1/admin/students/${editingStudentId.value}`, studentPayload())
      message.value = '学生资料已更新。'
      closeStudentEdit()
    } else {
      await api.post('/api/v1/admin/students', studentPayload())
      message.value = '学生已录入，账号处于待激活状态。'
      resetStudentForm()
    }
    await load()
  } catch (reason) { error.value = translateError(reason) }
  finally { savingStudent.value = false }
}

function fillStudentForm(student: DataObject) {
  studentForm.studentNumber = String(student.student_number)
  studentForm.studentName = String(student.student_name)
  studentForm.gender = String(student.gender) as StudentForm['gender']
  studentForm.majorId = Number(student.major_id)
  studentForm.nationalityCode = String(student.nationality_code ?? 'CN')
  studentForm.studentCategory = String(student.student_category ?? 'DOMESTIC') as StudentForm['studentCategory']
  studentForm.phoneNumber = String(student.phone_number ?? '')
  studentForm.degreeLevel = String(student.degree_level ?? '') as StudentForm['degreeLevel']
  studentForm.gradeYear = student.grade_year ? Number(student.grade_year) : null
}

function editStudent(student: DataObject) {
  editingStudentId.value = Number(student.id)
  editingStudent.value = student
  fillStudentForm(student)
  error.value = ''
  message.value = ''
}

function closeStudentEdit() {
  if (savingStudent.value) return
  editingStudentId.value = null
  editingStudent.value = null
  resetStudentForm()
}

function resetStudentForm() {
  studentForm.studentNumber = ''
  studentForm.studentName = ''
  studentForm.gender = 'M'
  studentForm.nationalityCode = 'CN'
  studentForm.studentCategory = 'DOMESTIC'
  studentForm.phoneNumber = ''
  studentForm.degreeLevel = ''
  studentForm.gradeYear = null
  if (majors.value.length) studentForm.majorId = Number(majors.value[0].id)
}

function chooseImportFile(event: Event) {
  importFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function importStudents() {
  if (!importFile.value || importing.value) return
  importing.value = true; error.value = ''; message.value = ''
  try {
    const form = new FormData()
    form.append('file', importFile.value)
    const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/import/students', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    const result = (response.data.data ?? {}) as DataObject
    message.value = `导入完成：成功${Number(result.successCount ?? result.success ?? 0)}条，跳过${Number(result.skippedCount ?? result.failed ?? 0)}条。`
    importFile.value = null
    await load()
  } catch (reason) { error.value = translateError(reason) }
  finally { importing.value = false }
}

async function downloadStudentTemplate(format: 'xlsx' | 'csv') {
  try {
    const response = await api.get('/api/v1/admin/import/students/template', {
      params: { format }, responseType: 'blob',
    })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url; anchor.download = `学生导入模板.${format}`; anchor.click()
    URL.revokeObjectURL(url)
  } catch (reason) { error.value = translateError(reason) }
}

function openReset(student: DataObject, mode: 'password' | 'state') {
  resetTarget.value = student; resetMode.value = mode
  resetForm.reason = ''; resetForm.confirmStudentNumber = ''
  error.value = ''; message.value = ''
}

function closeReset() { if (!resetting.value) resetTarget.value = null }

async function submitReset() {
  if (!resetTarget.value) return
  error.value = ''; message.value = ''; resetting.value = true
  try {
    const id = Number(resetTarget.value.id)
    if (resetMode.value === 'password') {
      await api.post(`/api/v1/admin/students/${id}/reset-password`, { reason: resetForm.reason.trim() })
      message.value = `${resetTarget.value.student_name}的密码已重置，账号恢复为待激活。`
    } else {
      await api.post(`/api/v1/admin/students/${id}/reset-state`, {
        confirmStudentNumber: resetForm.confirmStudentNumber.trim(),
        reason: resetForm.reason.trim(),
      })
      message.value = `${resetTarget.value.student_name}的账号、在住与选寝状态已完全重置。`
    }
    resetTarget.value = null
    await load()
  } catch (reason) { error.value = translateError(reason) }
  finally { resetting.value = false }
}

function categoryText(value: unknown) { return String(value) === 'INTERNATIONAL' ? '国际生' : '国内生' }
function degreeText(value: unknown) {
  return ({ UNDERGRADUATE: '本科生', MASTER: '硕士生', DOCTOR: '博士生', MASTER_DOCTOR: '硕博生' } as Record<string, string>)[String(value)] ?? '未填写'
}
function sourceText(value: unknown) {
  return ({ INITIAL_IMPORT: '初始名单', TRANSFER_MANUAL: '管理员录入', ADMIN_MANUAL: '管理员录入', BATCH_IMPORT: '批量导入' } as Record<string, string>)[String(value)] ?? '管理员录入'
}
</script>

<template>
  <div class="content-column">
    <div class="page-title data-title">
      <div><span class="eyebrow">{{ subtitle('基础数据', 'MASTER DATA') }}</span><h2>专业与学生</h2><p>统一维护专业目录和学生资料，支持单个录入、遮罩编辑及批量导入。</p></div>
      <div class="button-row wrap"><button class="button secondary" @click="downloadStudentTemplate('xlsx')">下载Excel模板</button><button class="button ghost" @click="downloadStudentTemplate('csv')">下载CSV模板</button></div>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <section class="panel import-panel">
      <div><span class="eyebrow">批量处理</span><h3>批量导入学生</h3><p>支持 Excel 和 CSV。请先下载模板，专业填写专业编号，国家或地区可填写中文或英文名称。</p></div>
      <div class="import-actions"><input class="input" type="file" accept=".xlsx,.xls,.csv,text/csv" @change="chooseImportFile" /><button class="button primary" :disabled="!importFile || importing" @click="importStudents">{{ importing ? '正在导入…' : '导入学生' }}</button></div>
    </section>

    <div class="admin-grid equal master-data-grid">
      <section class="panel master-data-card">
        <div class="section-head"><div><span class="eyebrow">专业设置</span><h3>专业目录</h3><p>专业数量较多时在卡片内部滚动，不影响页面布局。</p></div></div>
        <form class="form-grid" @submit.prevent="createMajor"><input v-model.trim="majorForm.majorCode" class="input" required maxlength="32" placeholder="专业编号" /><input v-model.trim="majorForm.majorName" class="input" required maxlength="128" placeholder="专业名称" /><button class="button primary">新增专业</button></form>
        <div class="simple-list major-scroll-list"><article v-for="major in majors" :key="String(major.id)"><div><strong>{{ major.major_name }}</strong><p>{{ major.major_code }}</p></div><button class="button ghost small" @click="toggleMajor(major)">{{ major.enabled ? '禁用' : '启用' }}</button></article><p v-if="majors.length === 0" class="empty-state">暂无专业。</p></div>
      </section>

      <section class="panel master-data-card">
        <div class="section-head"><div><span class="eyebrow">学生资料</span><h3>录入学生</h3><p>新增学生在此录入；修改已有学生时使用居中遮罩表单。</p></div></div>
        <form class="form-grid student-admin-form" @submit.prevent="saveStudent">
          <input v-model.trim="studentForm.studentNumber" class="input" required pattern="\d{12}" maxlength="12" placeholder="12位学号" />
          <input v-model.trim="studentForm.studentName" class="input" required maxlength="128" placeholder="姓名" />
          <select v-model="studentForm.gender" class="input" required><option value="M">男生</option><option value="F">女生</option></select>
          <select v-model.number="studentForm.majorId" class="input" required><option v-for="major in majors.filter((item) => item.enabled)" :key="String(major.id)" :value="Number(major.id)">{{ major.major_code }} · {{ major.major_name }}</option></select>
          <select v-model="studentForm.degreeLevel" class="input"><option value="">培养层次可留空</option><option value="UNDERGRADUATE">本科生</option><option value="MASTER">硕士生</option><option value="DOCTOR">博士生</option><option value="MASTER_DOCTOR">硕博生</option></select>
          <input v-model.number="studentForm.gradeYear" class="input" type="number" min="2000" max="2100" placeholder="年级，可留空，如2026" />
          <select v-model="studentForm.nationalityCode" class="input" :disabled="studentForm.studentCategory === 'DOMESTIC'"><option v-for="country in selectableCountries" :key="country.code" :value="country.code">{{ country.name }}</option></select>
          <input v-model.trim="studentForm.phoneNumber" class="input" maxlength="32" placeholder="手机号码，可留空" />
          <div class="segmented-control span-2"><button type="button" :class="{ active: studentForm.studentCategory === 'DOMESTIC' }" @click="setStudentCategory('DOMESTIC')">国内生</button><button type="button" :class="{ active: studentForm.studentCategory === 'INTERNATIONAL' }" @click="setStudentCategory('INTERNATIONAL')">国际生</button></div>
          <button class="button primary span-2" :disabled="savingStudent">{{ savingStudent ? '正在录入…' : '录入学生' }}</button>
        </form>
      </section>
    </div>

    <section class="panel">
      <div class="section-head split-title"><div><span class="eyebrow">学生名册</span><h3>学生列表</h3><p>共 {{ total }} 名学生</p></div><div class="student-filter-row"><input v-model.trim="keyword" class="input" placeholder="搜索学号或姓名" @keyup.enter="load" /><select v-model="categoryFilter" class="input" @change="load"><option value="">全部学生类别</option><option value="DOMESTIC">国内生</option><option value="INTERNATIONAL">国际生</option></select><button class="button secondary" @click="load">查询</button></div></div>
      <div class="table-wrap"><table><thead><tr><th>学生</th><th>专业与年级</th><th>类别</th><th>联系方式</th><th>录入方式</th><th>操作</th></tr></thead><tbody><tr v-for="student in students" :key="String(student.id)"><td><strong>{{ student.student_name }}</strong><small>{{ student.student_number }}</small></td><td>{{ student.major_name }}<small>{{ degreeText(student.degree_level) }} · {{ student.grade_year || '年级未填写' }}</small></td><td>{{ categoryText(student.student_category) }}<small>{{ countryLabel(String(student.nationality_code ?? 'CN')) }}</small></td><td>{{ student.phone_number || '未填写' }}</td><td>{{ sourceText(student.enrollment_source) }}</td><td><div class="button-row"><button class="button ghost small" @click="editStudent(student)">编辑</button><button class="button ghost small" @click="openReset(student, 'password')">重置密码</button><button class="button ghost small danger-text" @click="openReset(student, 'state')">完全重置</button></div></td></tr></tbody></table></div>
    </section>

    <div v-if="editingStudentId" class="modal-overlay student-edit-overlay" @click.self="closeStudentEdit"><section class="modal-card student-edit-dialog" role="dialog" aria-modal="true" aria-labelledby="student-edit-title"><header class="section-head split-title"><div><span class="eyebrow">学生资料修改</span><h3 id="student-edit-title">编辑 {{ editingStudent?.student_name }}</h3><p>{{ editingStudent?.student_number }}</p></div><button class="button ghost small" :disabled="savingStudent" @click="closeStudentEdit">关闭</button></header><form class="form-grid student-admin-form" @submit.prevent="saveStudent"><input v-model.trim="studentForm.studentNumber" class="input" required pattern="\d{12}" maxlength="12" placeholder="12位学号" /><input v-model.trim="studentForm.studentName" class="input" required maxlength="128" placeholder="姓名" /><select v-model="studentForm.gender" class="input" required><option value="M">男生</option><option value="F">女生</option></select><select v-model.number="studentForm.majorId" class="input" required><option v-for="major in majors.filter((item) => item.enabled)" :key="String(major.id)" :value="Number(major.id)">{{ major.major_code }} · {{ major.major_name }}</option></select><select v-model="studentForm.degreeLevel" class="input"><option value="">培养层次可留空</option><option value="UNDERGRADUATE">本科生</option><option value="MASTER">硕士生</option><option value="DOCTOR">博士生</option><option value="MASTER_DOCTOR">硕博生</option></select><input v-model.number="studentForm.gradeYear" class="input" type="number" min="2000" max="2100" placeholder="年级，可留空" /><select v-model="studentForm.nationalityCode" class="input" :disabled="studentForm.studentCategory === 'DOMESTIC'"><option v-for="country in selectableCountries" :key="country.code" :value="country.code">{{ country.name }}</option></select><input v-model.trim="studentForm.phoneNumber" class="input" maxlength="32" placeholder="手机号码，可留空" /><div class="segmented-control span-2"><button type="button" :class="{ active: studentForm.studentCategory === 'DOMESTIC' }" @click="setStudentCategory('DOMESTIC')">国内生</button><button type="button" :class="{ active: studentForm.studentCategory === 'INTERNATIONAL' }" @click="setStudentCategory('INTERNATIONAL')">国际生</button></div><div class="button-row span-2 edit-actions"><button class="button ghost" type="button" :disabled="savingStudent" @click="closeStudentEdit">取消</button><button class="button primary" :disabled="savingStudent">{{ savingStudent ? '正在保存…' : '保存修改' }}</button></div></form></section></div>

    <div v-if="resetTarget" class="modal-overlay student-reset-overlay" @click.self="closeReset"><section class="modal-card student-reset-dialog" role="dialog" aria-modal="true"><header class="section-head split-title"><div><span class="eyebrow">学生账号重置</span><h3>{{ resetMode === 'password' ? '重置学生密码' : '完全重置学生状态' }}</h3><p>{{ resetTarget.student_name }} · {{ resetTarget.student_number }}</p></div><button class="button ghost small" :disabled="resetting" @click="closeReset">关闭</button></header><div class="student-reset-warning" :class="{ danger: resetMode === 'state' }"><strong>{{ resetMode === 'password' ? '仅重置登录信息' : '不可恢复的完整重置' }}</strong><p v-if="resetMode === 'password'">清除密码和登录令牌，不影响寝室、个人偏好、组队及批次资格。</p><p v-else>结束当前在住，清除临时占用、密码、分配结果、个人偏好、组队关系、通知和批次资格，使学生恢复到待激活状态。</p></div><form class="form-stack" @submit.prevent="submitReset"><label v-if="resetMode === 'state'"><span>输入学号确认</span><input v-model.trim="resetForm.confirmStudentNumber" class="input" required pattern="\d{12}" maxlength="12" :placeholder="String(resetTarget.student_number)" /></label><label><span>操作原因</span><textarea v-model.trim="resetForm.reason" class="input" required maxlength="500" rows="3" /></label><div class="button-row"><button class="button ghost" type="button" @click="closeReset">取消</button><button class="button" :class="resetMode === 'state' ? 'danger' : 'primary'" :disabled="resetting">确认</button></div></form></section></div>
  </div>
</template>

<style scoped>
.data-title { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; }
.import-panel { display: flex; justify-content: space-between; gap: 24px; align-items: center; }
.import-actions { display: flex; gap: 10px; align-items: center; min-width: min(520px, 100%); }
.master-data-grid { align-items: stretch; }
.master-data-card { height: 570px; display: flex; flex-direction: column; overflow: hidden; }
.major-scroll-list { flex: 1; min-height: 0; overflow-y: auto; margin-top: 18px; padding-right: 5px; }
.student-admin-form { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.segmented-control { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; padding: 4px; border-radius: 12px; background: #eef2f8; }
.segmented-control button { min-height: 38px; border: 0; border-radius: 9px; color: var(--muted); background: transparent; cursor: pointer; }
.segmented-control button.active { color: #1e4e9f; background: #fff; box-shadow: 0 4px 12px rgba(26,50,90,.1); font-weight: 700; }
.student-filter-row { display: grid; grid-template-columns: minmax(220px, 1fr) 180px auto; gap: 10px; }
.danger-text { color: var(--danger); }
.student-edit-overlay { z-index: 1260; padding: 30px; background: rgba(9,23,48,.78); backdrop-filter: blur(7px); }
.student-edit-dialog { width: min(760px, calc(100vw - 60px)); padding: 26px; border-radius: 26px; background: var(--panel, #fff); }
.edit-actions { justify-content: flex-end; }
@media (max-width: 900px) { .data-title, .import-panel { display: grid; } .import-actions { min-width: 0; } .master-data-card { height: auto; max-height: none; } .major-scroll-list { max-height: 380px; } }
@media (max-width: 680px) { .student-admin-form, .student-filter-row { grid-template-columns: 1fr; } .student-admin-form .span-2 { grid-column: auto; } .import-actions { display: grid; } .student-edit-overlay { padding: 10px; }.student-edit-dialog { width: 100%; padding: 18px; border-radius: 22px; } }
</style>
