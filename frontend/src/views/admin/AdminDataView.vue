<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import ImportWorkflowModal from '../../components/admin/ImportWorkflowModal.vue'
import PaginationBar from '../../components/common/PaginationBar.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, MajorRequest, ObjectSuccessResponse } from '../../api/types'
import { countryLabel, countryOptions, domesticRegionOptions } from '../../utils/countries'
import { dialCodeForCountry, normalizeInternationalPhone, phoneCodeOptions, splitInternationalPhone } from '../../utils/phoneCodes'
import { bedTypeLabel } from '../../utils/bedLabels'
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

const currentYear = new Date().getFullYear()
const majors = ref<DataObject[]>([])
const students = ref<DataObject[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
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
const importOpen = ref(false)
const phoneDialCode = ref('+86')
const placementTarget = ref<DataObject | null>(null)
const placementLoading = ref(false)
const placementSaving = ref(false)
const placementForm = reactive({ newBedId: 0, reason: '' })
const resetForm = reactive({ reason: '', confirmStudentNumber: '' })
const majorForm = reactive<MajorRequest>({ majorCode: '', majorName: '', enabled: true })
const studentForm = reactive<StudentForm>({
  studentNumber: '', studentName: '', gender: 'M', majorId: 0,
  nationalityCode: 'CN', studentCategory: 'DOMESTIC', phoneNumber: '',
  degreeLevel: '', gradeYear: currentYear,
})
const selectableCountries = computed(() => studentForm.studentCategory === 'DOMESTIC'
  ? domesticRegionOptions
  : countryOptions.filter((country) => !['CN', 'HK', 'MO', 'TW'].includes(country.code)))
const targetBeds = computed(() => (placementTarget.value?.availableBeds ?? []) as DataObject[])
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
          page: page.value,
          size: pageSize.value,
        },
      }),
    ])
    majors.value = (majorResponse.data.data ?? []) as DataObject[]
    const studentData = (studentResponse.data.data ?? {}) as DataObject
    students.value = (studentData.items ?? []) as DataObject[]
    total.value = Number(studentData.total ?? 0)
    page.value = Number(studentData.page ?? page.value)
    pageSize.value = Number(studentData.size ?? pageSize.value)
    if (!studentForm.majorId && majors.value.length) studentForm.majorId = Number(majors.value[0].id)
  } catch (reason) {
    error.value = translateError(reason)
  }
}

function searchStudents() {
  page.value = 1
  void load()
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
  if (category === 'DOMESTIC') {
    if (!['CN', 'HK', 'MO', 'TW'].includes(studentForm.nationalityCode)) studentForm.nationalityCode = 'CN'
  } else if (['CN', 'HK', 'MO', 'TW'].includes(studentForm.nationalityCode)) {
    studentForm.nationalityCode = 'US'
  }
  syncDialCode()
}

function syncDialCode() {
  phoneDialCode.value = dialCodeForCountry(studentForm.nationalityCode)
}

function studentPayload() {
  return {
    ...studentForm,
    enrollmentSource: 'ADMIN_MANUAL',
    nationalityCode: String(studentForm.nationalityCode || 'CN').trim().toUpperCase(),
    phoneNumber: normalizeInternationalPhone(phoneDialCode.value, studentForm.phoneNumber),
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
  const phone = splitInternationalPhone(student.phone_number, student.nationality_code)
  phoneDialCode.value = phone.dialCode
  studentForm.phoneNumber = phone.localNumber
  studentForm.degreeLevel = String(student.degree_level ?? '') as StudentForm['degreeLevel']
  studentForm.gradeYear = student.grade_year ? Number(student.grade_year) : currentYear
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
  phoneDialCode.value = '+86'
  studentForm.phoneNumber = ''
  studentForm.degreeLevel = ''
  studentForm.gradeYear = currentYear
  if (majors.value.length) studentForm.majorId = Number(majors.value[0].id)
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

function importCommitted() {
  importOpen.value = false
  message.value = '学生文件已通过预检并完成导入。'
  page.value = 1
  void load()
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

async function openPlacement(student: DataObject) {
  if (placementLoading.value) return
  placementLoading.value = true
  error.value = ''
  message.value = ''
  try {
    const batchResponse = await api.get<ListSuccessResponse>('/api/v1/admin/batches')
    const batches = (batchResponse.data.data ?? []) as DataObject[]
    const results = await Promise.all(batches.map(async (batch) => {
      try {
        const response = await api.get<ListSuccessResponse>(`/api/v1/admin/batches/${batch.id}/assignments`, {
          params: { keyword: String(student.student_number) },
        })
        return (response.data.data ?? []) as DataObject[]
      } catch {
        return [] as DataObject[]
      }
    }))
    const assignment = results.flat().find((item) => String(item.student_number) === String(student.student_number))
    if (!assignment) {
      error.value = '该学生当前没有可调整的有效床位分配。房间模式下尚未确认床位的学生，请先到“在住与床位确认”页面确认实际床位。'
      return
    }
    placementTarget.value = assignment
    const available = (assignment.availableBeds ?? []) as DataObject[]
    placementForm.newBedId = available.length ? Number(available[0].bed_id) : 0
    placementForm.reason = ''
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    placementLoading.value = false
  }
}

function closePlacement() {
  if (placementSaving.value) return
  placementTarget.value = null
  placementForm.newBedId = 0
  placementForm.reason = ''
}

async function savePlacement() {
  if (!placementTarget.value || !placementForm.newBedId || !placementForm.reason.trim()) return
  placementSaving.value = true
  error.value = ''
  try {
    await api.post(`/api/v1/admin/assignments/${placementTarget.value.assignment_id}/adjust`, {
      newBedId: placementForm.newBedId,
      reason: placementForm.reason.trim(),
    })
    message.value = `${placementTarget.value.student_name}的寝室与床位已调整，系统已完成批次范围、性别、运行状态和占用校验。`
    closePlacementAfterSave()
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    placementSaving.value = false
  }
}

function closePlacementAfterSave() {
  placementTarget.value = null
  placementForm.newBedId = 0
  placementForm.reason = ''
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
      <div><span class="eyebrow">{{ subtitle('基础数据', 'MASTER DATA') }}</span><h2>专业与学生</h2><p>统一维护专业目录、学生身份、联系方式、住宿归属和批量数据。</p></div>
      <div class="button-row wrap"><button class="button secondary" @click="downloadStudentTemplate('xlsx')">下载Excel模板</button><button class="button ghost" @click="downloadStudentTemplate('csv')">下载CSV模板</button><button class="button primary" @click="importOpen = true">批量导入</button></div>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <div class="admin-grid equal master-data-grid">
      <section class="panel master-data-card">
        <div class="section-head compact-section-head"><div><span class="eyebrow">专业设置</span><h3>专业目录</h3><p>新增、停用和查看学生录入时可选择的专业。</p></div></div>
        <form class="form-grid" @submit.prevent="createMajor"><input v-model.trim="majorForm.majorCode" class="input" required maxlength="32" placeholder="专业编号" /><input v-model.trim="majorForm.majorName" class="input" required maxlength="128" placeholder="专业名称" /><button class="button primary">新增专业</button></form>
        <div class="simple-list major-scroll-list"><article v-for="major in majors" :key="String(major.id)"><div><strong>{{ major.major_name }}</strong><p>{{ major.major_code }}</p></div><button class="button ghost small" @click="toggleMajor(major)">{{ major.enabled ? '禁用' : '启用' }}</button></article><p v-if="majors.length === 0" class="empty-state">暂无专业。</p></div>
      </section>

      <section class="panel master-data-card">
        <div class="section-head compact-section-head"><div><span class="eyebrow">学生资料</span><h3>录入学生</h3><p>填写学生基本身份和联系方式，录入后自动创建待激活账号。</p></div></div>
        <form class="form-grid student-admin-form" @submit.prevent="saveStudent">
          <input v-model.trim="studentForm.studentNumber" class="input" required pattern="\d{12}" maxlength="12" placeholder="12位学号" />
          <input v-model.trim="studentForm.studentName" class="input" required maxlength="128" placeholder="姓名" />
          <select v-model="studentForm.gender" class="input" required><option value="M">男生</option><option value="F">女生</option></select>
          <select v-model.number="studentForm.majorId" class="input" required><option v-for="major in majors.filter((item) => item.enabled)" :key="String(major.id)" :value="Number(major.id)">{{ major.major_code }} · {{ major.major_name }}</option></select>
          <select v-model="studentForm.degreeLevel" class="input"><option value="">培养层次可留空</option><option value="UNDERGRADUATE">本科生</option><option value="MASTER">硕士生</option><option value="DOCTOR">博士生</option><option value="MASTER_DOCTOR">硕博生</option></select>
          <input v-model.number="studentForm.gradeYear" class="input" type="number" min="2000" max="2100" :placeholder="`入学年级，默认${currentYear}`" />
          <select v-model="studentForm.nationalityCode" class="input" @change="syncDialCode"><option v-for="country in selectableCountries" :key="country.code" :value="country.code">{{ country.name }}</option></select>
          <div class="phone-input-group"><select v-model="phoneDialCode" class="input phone-code" aria-label="手机国家码"><option v-for="item in phoneCodeOptions" :key="item.countryCode" :value="item.dialCode">{{ item.label }}</option></select><input v-model.trim="studentForm.phoneNumber" class="input" inputmode="tel" maxlength="24" placeholder="手机号码，可留空" /></div>
          <div class="segmented-control span-2"><button type="button" :class="{ active: studentForm.studentCategory === 'DOMESTIC' }" @click="setStudentCategory('DOMESTIC')">国内生</button><button type="button" :class="{ active: studentForm.studentCategory === 'INTERNATIONAL' }" @click="setStudentCategory('INTERNATIONAL')">国际生</button></div>
          <button class="button primary span-2" :disabled="savingStudent">{{ savingStudent ? '正在录入…' : '录入学生' }}</button>
        </form>
      </section>
    </div>

    <section class="panel">
      <div class="section-head split-title compact-section-head"><div><span class="eyebrow">学生名册</span><h3>学生列表</h3><p>共 {{ total }} 名学生</p></div><div class="student-filter-row"><input v-model.trim="keyword" class="input" placeholder="搜索学号、姓名或手机号" @keyup.enter="searchStudents" /><select v-model="categoryFilter" class="input" @change="searchStudents"><option value="">全部学生类别</option><option value="DOMESTIC">国内生</option><option value="INTERNATIONAL">国际生</option></select><button class="button secondary" @click="searchStudents">查询</button></div></div>
      <div class="table-wrap"><table><thead><tr><th>学生</th><th>专业与年级</th><th>类别</th><th>联系方式</th><th>录入方式</th><th>操作</th></tr></thead><tbody><tr v-for="student in students" :key="String(student.id)"><td><strong>{{ student.student_name }}</strong><small>{{ student.student_number }}</small></td><td>{{ student.major_name }}<small>{{ degreeText(student.degree_level) }} · {{ student.grade_year || '年级未填写' }}</small></td><td>{{ categoryText(student.student_category) }}<small>{{ countryLabel(String(student.nationality_code ?? 'CN')) }}</small></td><td>{{ student.phone_number || '未填写' }}</td><td>{{ sourceText(student.enrollment_source) }}</td><td><div class="button-row compact-actions"><button class="button ghost small" @click="editStudent(student)">编辑</button><button class="button secondary small" :disabled="placementLoading" @click="openPlacement(student)">修改寝室/床位</button><button class="button ghost small" @click="openReset(student, 'password')">重置密码</button><button class="button ghost small danger-text" @click="openReset(student, 'state')">完全重置</button></div></td></tr></tbody></table></div>
      <PaginationBar v-model:page="page" v-model:page-size="pageSize" :total="total" @change="load" />
    </section>

    <ImportWorkflowModal :open="importOpen" import-type="STUDENT" title="批量导入学生" @close="importOpen = false" @committed="importCommitted" />

    <div v-if="editingStudentId" class="modal-overlay student-edit-overlay" @click.self="closeStudentEdit"><section class="modal-card student-edit-dialog" role="dialog" aria-modal="true" aria-labelledby="student-edit-title"><header class="section-head split-title compact-section-head"><div><span class="eyebrow">学生资料修改</span><h3 id="student-edit-title">编辑 {{ editingStudent?.student_name }}</h3><p>{{ editingStudent?.student_number }}</p></div><button class="button ghost small" :disabled="savingStudent" @click="closeStudentEdit">关闭</button></header><form class="form-grid student-admin-form" @submit.prevent="saveStudent"><input v-model.trim="studentForm.studentNumber" class="input" required pattern="\d{12}" maxlength="12" placeholder="12位学号" /><input v-model.trim="studentForm.studentName" class="input" required maxlength="128" placeholder="姓名" /><select v-model="studentForm.gender" class="input" required><option value="M">男生</option><option value="F">女生</option></select><select v-model.number="studentForm.majorId" class="input" required><option v-for="major in majors.filter((item) => item.enabled)" :key="String(major.id)" :value="Number(major.id)">{{ major.major_code }} · {{ major.major_name }}</option></select><select v-model="studentForm.degreeLevel" class="input"><option value="">培养层次可留空</option><option value="UNDERGRADUATE">本科生</option><option value="MASTER">硕士生</option><option value="DOCTOR">博士生</option><option value="MASTER_DOCTOR">硕博生</option></select><input v-model.number="studentForm.gradeYear" class="input" type="number" min="2000" max="2100" placeholder="入学年级" /><select v-model="studentForm.nationalityCode" class="input" @change="syncDialCode"><option v-for="country in selectableCountries" :key="country.code" :value="country.code">{{ country.name }}</option></select><div class="phone-input-group"><select v-model="phoneDialCode" class="input phone-code"><option v-for="item in phoneCodeOptions" :key="item.countryCode" :value="item.dialCode">{{ item.label }}</option></select><input v-model.trim="studentForm.phoneNumber" class="input" inputmode="tel" maxlength="24" placeholder="手机号码，可留空" /></div><div class="segmented-control span-2"><button type="button" :class="{ active: studentForm.studentCategory === 'DOMESTIC' }" @click="setStudentCategory('DOMESTIC')">国内生</button><button type="button" :class="{ active: studentForm.studentCategory === 'INTERNATIONAL' }" @click="setStudentCategory('INTERNATIONAL')">国际生</button></div><div class="button-row span-2 edit-actions"><button class="button ghost" type="button" :disabled="savingStudent" @click="closeStudentEdit">取消</button><button class="button primary" :disabled="savingStudent">{{ savingStudent ? '正在保存…' : '保存修改' }}</button></div></form></section></div>

    <div v-if="placementTarget" class="modal-overlay placement-overlay" @click.self="closePlacement"><section class="modal-card placement-dialog" role="dialog" aria-modal="true"><header class="section-head split-title compact-section-head"><div><span class="eyebrow">住宿调整</span><h3>修改 {{ placementTarget.student_name }} 的寝室或床位</h3><p>当前：{{ placementTarget.building_name }} {{ placementTarget.room_number }} · {{ placementTarget.bed_code }}</p></div><button class="button ghost small" :disabled="placementSaving" @click="closePlacement">关闭</button></header><form class="form-stack" @submit.prevent="savePlacement"><label><span>目标寝室与床位</span><select v-model.number="placementForm.newBedId" class="input" required><option v-for="bed in targetBeds" :key="String(bed.bed_id)" :value="Number(bed.bed_id)">{{ bed.display_name }} · {{ bedTypeLabel(bed.bed_type) }}</option></select><small>仅展示当前批次范围内、同性别、已启用且未被占用的床位。</small></label><label><span>调整原因</span><textarea v-model.trim="placementForm.reason" class="input" rows="3" required minlength="2" maxlength="500" placeholder="例如：原寝室进入维护状态"></textarea></label><div class="button-row dialog-actions"><button class="button ghost" type="button" @click="closePlacement">取消</button><button class="button primary" :disabled="!targetBeds.length || placementSaving">{{ placementSaving ? '正在调整…' : '确认调整' }}</button></div></form></section></div>

    <div v-if="resetTarget" class="modal-overlay student-reset-overlay" @click.self="closeReset"><section class="modal-card student-reset-dialog" role="dialog" aria-modal="true"><header class="section-head split-title compact-section-head"><div><span class="eyebrow">学生账号重置</span><h3>{{ resetMode === 'password' ? '重置学生密码' : '完全重置学生状态' }}</h3><p>{{ resetTarget.student_name }} · {{ resetTarget.student_number }}</p></div><button class="button ghost small" :disabled="resetting" @click="closeReset">关闭</button></header><div class="student-reset-warning" :class="{ danger: resetMode === 'state' }"><strong>{{ resetMode === 'password' ? '仅重置登录信息' : '不可恢复的完整重置' }}</strong><p v-if="resetMode === 'password'">清除密码和登录令牌，不影响寝室、个人偏好、组队及批次资格。</p><p v-else>结束当前在住，清除临时占用、密码、分配结果、个人偏好、组队关系、通知和批次资格，使学生恢复到待激活状态。</p></div><form class="form-stack" @submit.prevent="submitReset"><label v-if="resetMode === 'state'"><span>输入学号确认</span><input v-model.trim="resetForm.confirmStudentNumber" class="input" required pattern="\d{12}" maxlength="12" :placeholder="String(resetTarget.student_number)" /></label><label><span>操作原因</span><textarea v-model.trim="resetForm.reason" class="input" required maxlength="500" rows="3" /></label><div class="button-row dialog-actions"><button class="button ghost" type="button" @click="closeReset">取消</button><button class="button" :class="resetMode === 'state' ? 'danger' : 'primary'" :disabled="resetting">确认</button></div></form></section></div>
  </div>
</template>

<style scoped>
.data-title{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.compact-section-head{margin-bottom:12px}.master-data-grid{align-items:stretch}.master-data-card{height:570px;display:flex;flex-direction:column;overflow:hidden}.major-scroll-list{flex:1;min-height:0;overflow-y:auto;margin-top:12px;padding-right:5px}.student-admin-form{grid-template-columns:repeat(2,minmax(0,1fr))}.phone-input-group{display:grid;grid-template-columns:minmax(132px,.8fr) minmax(150px,1.2fr);gap:7px}.phone-code{min-width:0}.segmented-control{display:grid;grid-template-columns:1fr 1fr;gap:4px;padding:4px;border-radius:12px;background:#eef2f8}.segmented-control button{min-height:38px;border:0;border-radius:9px;color:var(--muted);background:transparent;cursor:pointer}.segmented-control button.active{color:#1e4e9f;background:#fff;box-shadow:0 4px 12px rgba(26,50,90,.1);font-weight:700}.student-filter-row{display:grid;grid-template-columns:minmax(220px,1fr) 180px auto;gap:10px}.compact-actions{flex-wrap:wrap}.danger-text{color:var(--danger)}.student-edit-overlay,.student-reset-overlay,.placement-overlay{z-index:1260;padding:30px;background:rgba(9,23,48,.78);backdrop-filter:blur(7px)}.student-edit-dialog{width:min(760px,calc(100vw - 60px));padding:26px;border-radius:26px;background:var(--panel,#fff)}.student-reset-dialog,.placement-dialog{width:min(620px,calc(100vw - 60px));padding:26px;border-radius:26px;background:var(--panel,#fff);box-shadow:0 26px 70px rgba(3,14,34,.3)}.student-reset-warning{margin-bottom:18px;padding:15px;border:1px solid #d7e5fa;border-radius:14px;background:#f4f8ff}.student-reset-warning.danger{border-color:#fecaca;color:#991b1b;background:#fff1f2}.student-reset-warning p{margin:6px 0 0;line-height:1.65}.placement-dialog label{display:grid;gap:7px}.placement-dialog label small{color:var(--muted)}.edit-actions,.dialog-actions{justify-content:flex-end}@media(max-width:900px){.data-title{display:grid}.master-data-card{height:auto;max-height:none}.major-scroll-list{max-height:380px}}@media(max-width:680px){.student-admin-form,.student-filter-row,.phone-input-group{grid-template-columns:1fr}.student-admin-form .span-2{grid-column:auto}.student-edit-overlay,.student-reset-overlay,.placement-overlay{padding:10px}.student-edit-dialog,.student-reset-dialog,.placement-dialog{width:100%;padding:18px;border-radius:22px}}
</style>
