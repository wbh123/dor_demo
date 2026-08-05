<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppModal from '../../components/modal/AppModal.vue'
import ImportWorkflowModal from '../../components/admin/ImportWorkflowModal.vue'
import PhoneDialCodeSelect from '../../components/common/PhoneDialCodeSelect.vue'
import PaginationBar from '../../components/common/PaginationBar.vue'
import TransientNotice from '../../components/common/TransientNotice.vue'
import RoomBedScene3D from '../../components/student/RoomBedScene3D.vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, MajorRequest, ObjectSuccessResponse } from '../../api/types'
import { countryLabel, countryOptions, domesticRegionOptions } from '../../utils/countries'
import { dialCodeForCountry, formatPhoneDisplay, normalizeInternationalPhone, splitInternationalPhone } from '../../utils/phoneCodes'
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
const notice = ref('')
const noticeType = ref<'success' | 'error' | 'warning' | 'info'>('success')
const loading = ref(false)
const editingStudent = ref<DataObject | null>(null)
const savingStudent = ref(false)
const importOpen = ref(false)
const resetTarget = ref<DataObject | null>(null)
const resetMode = ref<'password' | 'state'>('password')
const resetting = ref(false)
const placementTarget = ref<DataObject | null>(null)
const placementLoadingId = ref<number | null>(null)
const placementSaving = ref(false)
const placementRoomId = ref(0)
const phoneDialCode = ref('+86')
const majorForm = reactive<MajorRequest>({ majorCode: '', majorName: '', enabled: true })
const resetForm = reactive({ reason: '', confirmStudentNumber: '' })
const placementForm = reactive({ bedId: 0, reason: '' })
const studentForm = reactive<StudentForm>({
  studentNumber: '', studentName: '', gender: 'M', majorId: 0,
  nationalityCode: 'CN', studentCategory: 'DOMESTIC', phoneNumber: '',
  degreeLevel: '', gradeYear: currentYear,
})

const { subtitle, translateError } = useI18n()
const enabledMajors = computed(() => majors.value.filter((item) => Boolean(item.enabled)))
const selectableCountries = computed(() => studentForm.studentCategory === 'DOMESTIC'
  ? domesticRegionOptions
  : countryOptions.filter((country) => !['CN', 'HK', 'MO', 'TW'].includes(country.code)))
const placementBeds = computed(() => (placementTarget.value?.availableBeds ?? []) as DataObject[])
const placementRooms = computed(() => [...new Map(placementBeds.value.map((bed) => [Number(bed.room_id), { roomId: Number(bed.room_id), label: `${bed.building_name} ${bed.room_number} · ${bed.floor_number}层` }])).values()])
const placementRoomBeds = computed(() => placementBeds.value.filter((bed) => Number(bed.room_id) === placementRoomId.value))
const placementSceneBeds = computed(() => placementRoomBeds.value.map((bed) => ({ ...bed, id: Number(bed.bed_id), operational_status: 'ENABLED' })))
const currentResidency = computed(() => (placementTarget.value?.currentResidency ?? {}) as DataObject)

onMounted(load)

async function load() {
  loading.value = true
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
    const data = (studentResponse.data.data ?? {}) as DataObject
    students.value = (data.items ?? []) as DataObject[]
    total.value = Number(data.total ?? 0)
    page.value = Number(data.page ?? page.value)
    pageSize.value = Number(data.size ?? pageSize.value)
    if (!studentForm.majorId && enabledMajors.value.length) {
      studentForm.majorId = Number(enabledMajors.value[0].id)
    }
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    loading.value = false
  }
}

function showNotice(message: string, type: 'success' | 'error' | 'warning' | 'info' = 'success') {
  notice.value = ''
  noticeType.value = type
  window.setTimeout(() => { notice.value = message }, 0)
}

function searchStudents() {
  page.value = 1
  void load()
}

async function createMajor() {
  error.value = ''
  try {
    await api.post('/api/v1/admin/majors', majorForm)
    majorForm.majorCode = ''
    majorForm.majorName = ''
    majorForm.enabled = true
    showNotice('专业已创建。')
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  }
}

async function toggleMajor(major: DataObject) {
  error.value = ''
  try {
    await api.put(`/api/v1/admin/majors/${major.id}`, {
      majorCode: major.major_code,
      majorName: major.major_name,
      enabled: !Boolean(major.enabled),
    })
    showNotice(Boolean(major.enabled) ? '专业已停用。' : '专业已启用。')
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  }
}

function setStudentCategory(category: StudentForm['studentCategory']) {
  studentForm.studentCategory = category
  if (category === 'DOMESTIC') {
    if (!['CN', 'HK', 'MO', 'TW'].includes(studentForm.nationalityCode)) {
      studentForm.nationalityCode = 'CN'
    }
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
    studentNumber: studentForm.studentNumber.trim(),
    studentName: studentForm.studentName.trim(),
    gender: studentForm.gender,
    majorId: studentForm.majorId,
    nationalityCode: studentForm.nationalityCode,
    studentCategory: studentForm.studentCategory,
    enrollmentSource: 'ADMIN_MANUAL',
    phoneNumber: normalizeInternationalPhone(phoneDialCode.value, studentForm.phoneNumber),
    degreeLevel: studentForm.degreeLevel || undefined,
    gradeYear: studentForm.gradeYear || undefined,
  }
}

async function saveStudent() {
  if (savingStudent.value) return
  savingStudent.value = true
  error.value = ''
  try {
    if (editingStudent.value) {
      await api.put(`/api/v1/admin/students/${editingStudent.value.id}`, studentPayload())
      showNotice('学生资料已更新。')
      closeStudentEdit(true)
    } else {
      await api.post('/api/v1/admin/students', studentPayload())
      showNotice('学生已录入，账号处于待激活状态。')
      resetStudentForm()
    }
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    savingStudent.value = false
  }
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
  editingStudent.value = student
  fillStudentForm(student)
  error.value = ''
}

function closeStudentEdit(force = false) {
  if (savingStudent.value && !force) return
  editingStudent.value = null
  resetStudentForm()
}

function resetStudentForm() {
  studentForm.studentNumber = ''
  studentForm.studentName = ''
  studentForm.gender = 'M'
  studentForm.majorId = enabledMajors.value.length ? Number(enabledMajors.value[0].id) : 0
  studentForm.studentCategory = 'DOMESTIC'
  studentForm.nationalityCode = 'CN'
  studentForm.phoneNumber = ''
  studentForm.degreeLevel = ''
  studentForm.gradeYear = currentYear
  phoneDialCode.value = '+86'
}

async function downloadTemplate(format: 'xlsx' | 'csv') {
  error.value = ''
  try {
    const response = await api.get('/api/v1/admin/import/students/template', {
      params: { format }, responseType: 'blob',
    })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `学生导入模板.${format}`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (reason) {
    error.value = translateError(reason)
  }
}

function importCommitted() {
  importOpen.value = false
  page.value = 1
  showNotice('学生文件已通过预检并完成导入。')
  void load()
}

function openReset(student: DataObject, mode: 'password' | 'state') {
  resetTarget.value = student
  resetMode.value = mode
  resetForm.reason = ''
  resetForm.confirmStudentNumber = ''
  error.value = ''
}

function closeReset() {
  if (!resetting.value) resetTarget.value = null
}

async function submitReset() {
  if (!resetTarget.value || resetting.value) return
  resetting.value = true
  error.value = ''
  try {
    const id = Number(resetTarget.value.id)
    if (resetMode.value === 'password') {
      await api.post(`/api/v1/admin/students/${id}/reset-password`, { reason: resetForm.reason.trim() })
      showNotice(`${resetTarget.value.student_name}的密码已重置。`)
    } else {
      await api.post(`/api/v1/admin/students/${id}/reset-state`, {
        confirmStudentNumber: resetForm.confirmStudentNumber.trim(),
        reason: resetForm.reason.trim(),
      })
      showNotice(`${resetTarget.value.student_name}的账号、在住与选寝状态已完全重置。`)
    }
    resetTarget.value = null
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    resetting.value = false
  }
}

async function openPlacement(student: DataObject) {
  if (placementLoadingId.value !== null) return
  if (!Boolean(student.currently_resident)) {
    showNotice('该学生当前未入住，将进入首次分配寝室和床位流程。', 'info')
  }
  placementLoadingId.value = Number(student.id)
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>(
      `/api/v1/admin/students/${student.id}/residency-adjustment-context`,
    )
    placementTarget.value = (response.data.data ?? {}) as DataObject
    placementRoomId.value = placementBeds.value.length ? Number(placementBeds.value[0].room_id) : 0
    placementForm.bedId = placementRoomBeds.value.length ? Number(placementRoomBeds.value[0].bed_id) : 0
    placementForm.reason = ''
  } catch (reason) {
    showNotice(translateError(reason), 'warning')
  } finally {
    placementLoadingId.value = null
  }
}

function closePlacement() {
  if (placementSaving.value) return
  placementTarget.value = null
  placementRoomId.value = 0
  placementForm.bedId = 0
  placementForm.reason = ''
}

async function savePlacement() {
  if (!placementTarget.value || !placementForm.bedId || !placementForm.reason.trim()) return
  placementSaving.value = true
  error.value = ''
  try {
    await api.post(
      `/api/v1/admin/students/${placementTarget.value.studentId}/residency-adjustment`,
      { bedId: placementForm.bedId, reason: placementForm.reason.trim() },
    )
    showNotice(Boolean(placementTarget.value.resident)
      ? `${placementTarget.value.studentName}的寝室和床位已调整。`
      : `${placementTarget.value.studentName}已分配寝室和床位。`)
    closePlacementAfterSave()
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    placementSaving.value = false
  }
}

function choosePlacementRoom() {
  placementForm.bedId = placementRoomBeds.value.length ? Number(placementRoomBeds.value[0].bed_id) : 0
}
function selectPlacementBed(bed: DataObject) {
  placementForm.bedId = Number(bed.id)
}
function closePlacementAfterSave() {
  placementTarget.value = null
  placementRoomId.value = 0
  placementForm.bedId = 0
  placementForm.reason = ''
}

function categoryText(value: unknown) { return String(value) === 'INTERNATIONAL' ? '国际生' : '国内生' }
function degreeText(value: unknown) {
  return ({ UNDERGRADUATE: '本科生', MASTER: '硕士生', DOCTOR: '博士生', MASTER_DOCTOR: '硕博生' } as Record<string, string>)[String(value)] ?? '未填写'
}
function sourceText(value: unknown) {
  return ({ INITIAL_IMPORT: '初始名单', ADMIN_MANUAL: '管理员录入', BATCH_IMPORT: '批量导入' } as Record<string, string>)[String(value)] ?? '管理员录入'
}
</script>

<template>
  <div class="content-column">
    <TransientNotice :message="notice" :type="noticeType" @close="notice = ''" />
    <div class="page-title data-title">
      <div><span class="eyebrow">{{ subtitle('基础数据', 'MASTER DATA') }}</span><h2>专业与学生</h2><p>统一维护专业目录、学生身份、联系方式和住宿归属。</p></div>
      <div class="button-row wrap"><button class="button secondary" @click="downloadTemplate('xlsx')">下载Excel模板</button><button class="button ghost" @click="downloadTemplate('csv')">下载CSV模板</button><button class="button primary" @click="importOpen = true">批量导入</button></div>
    </div>
    <p v-if="error" class="alert error">{{ error }}</p>

    <div class="admin-grid equal master-data-grid">
      <section class="panel master-data-card">
        <div class="section-head compact-section-head"><div><span class="eyebrow">专业设置</span><h3>专业目录</h3><p>新增、停用和查看学生录入时可选择的专业。</p></div></div>
        <div class="master-data-card-body major-card-body">
          <form class="form-grid major-create-form" @submit.prevent="createMajor"><input v-model.trim="majorForm.majorCode" class="input" required maxlength="32" placeholder="专业编号" /><input v-model.trim="majorForm.majorName" class="input" required maxlength="128" placeholder="专业名称" /><button class="button primary span-2">新增专业</button></form>
          <div class="simple-list major-scroll-list"><article v-for="major in majors" :key="String(major.id)"><div><strong>{{ major.major_name }}</strong><p>{{ major.major_code }}</p></div><button class="button ghost small" @click="toggleMajor(major)">{{ major.enabled ? '禁用' : '启用' }}</button></article><p v-if="majors.length === 0" class="empty-state">暂无专业。</p></div>
        </div>
      </section>

      <section class="panel master-data-card">
        <div class="section-head compact-section-head"><div><span class="eyebrow">学生资料</span><h3>录入学生</h3><p>录入身份和联系方式后自动创建待激活账号。</p></div></div>
        <div class="master-data-card-body student-card-body">
          <form class="form-grid student-admin-form" @submit.prevent="saveStudent">
            <div class="segmented-control student-category-switch-top span-2"><button type="button" :class="{ active: studentForm.studentCategory === 'DOMESTIC' }" @click="setStudentCategory('DOMESTIC')">国内生</button><button type="button" :class="{ active: studentForm.studentCategory === 'INTERNATIONAL' }" @click="setStudentCategory('INTERNATIONAL')">国际生</button></div><div class="student-contact-fields span-2"><select v-model="studentForm.nationalityCode" class="input nationality-select" @change="syncDialCode"><option v-for="country in selectableCountries" :key="country.code" :value="country.code">{{ country.name }}</option></select><div class="phone-input-group"><PhoneDialCodeSelect v-model="phoneDialCode" /><input v-model.trim="studentForm.phoneNumber" class="input" inputmode="tel" maxlength="24" placeholder="手机号码，可留空" /></div></div>
            <input v-model.trim="studentForm.studentNumber" class="input" required pattern="\d{12}" maxlength="12" placeholder="12位学号" />
            <input v-model.trim="studentForm.studentName" class="input" required maxlength="128" placeholder="姓名" />
            <select v-model="studentForm.gender" class="input"><option value="M">男生</option><option value="F">女生</option></select>
            <select v-model.number="studentForm.majorId" class="input" required><option v-for="major in enabledMajors" :key="String(major.id)" :value="Number(major.id)">{{ major.major_code }} · {{ major.major_name }}</option></select>
            <select v-model="studentForm.degreeLevel" class="input"><option value="">培养层次可留空</option><option value="UNDERGRADUATE">本科生</option><option value="MASTER">硕士生</option><option value="DOCTOR">博士生</option><option value="MASTER_DOCTOR">硕博生</option></select>
            <input v-model.number="studentForm.gradeYear" class="input" type="number" min="2000" max="2100" :placeholder="`入学年级，默认${currentYear}`" />
                        <button class="button primary span-2 student-submit" :disabled="savingStudent">{{ savingStudent ? '正在录入…' : '录入学生' }}</button>
          </form>
        </div>
      </section>
    </div>

    <section class="panel">
      <div class="section-head split-title compact-section-head"><div><span class="eyebrow">学生名册</span><h3>学生列表</h3><p>共 {{ total }} 名学生</p></div><div class="student-filter-row"><input v-model.trim="keyword" class="input" placeholder="搜索学号、姓名或手机号" @keyup.enter="searchStudents" /><select v-model="categoryFilter" class="input" @change="searchStudents"><option value="">全部类别</option><option value="DOMESTIC">国内生</option><option value="INTERNATIONAL">国际生</option></select><button class="button secondary" @click="searchStudents">查询</button></div></div>
      <p v-if="loading" class="empty-state">正在加载学生资料…</p>
      <div v-else class="table-wrap"><table><thead><tr><th>学生</th><th>专业与年级</th><th>类别</th><th>住宿状态</th><th>宿舍与床位</th><th>联系方式</th><th>录入方式</th><th>操作</th></tr></thead><tbody><tr v-for="student in students" :key="String(student.id)"><td><strong>{{ student.student_name }}</strong><small>{{ student.student_number }}</small></td><td>{{ student.major_name }}<small>{{ degreeText(student.degree_level) }} · {{ student.grade_year || '年级未填写' }}</small></td><td>{{ categoryText(student.student_category) }}<small>{{ countryLabel(String(student.nationality_code ?? 'CN')) }}</small></td><td><span class="status-chip compact" :class="{ warning: student.selection_review_status === 'PENDING' }">{{ student.selection_review_status === 'PENDING' ? '待审核' : student.currently_resident ? '已入住' : '未入住' }}</span></td><td><template v-if="student.currently_resident"><strong>{{ student.current_building_name }} {{ student.current_room_number }}</strong><small>正式床位：{{ student.current_bed_code ? `${student.current_bed_code} · ${bedTypeLabel(student.current_bed_type)}` : '待确认' }}</small><small v-if="student.selection_review_status === 'PENDING'">学生已选择：{{ student.declared_bed_code }} · {{ bedTypeLabel(student.declared_bed_type) }}</small></template><span v-else>暂无住宿</span></td><td><span v-if="student.phone_number" class="phone-display-text" :title="formatPhoneDisplay(student.phone_number, student.nationality_code)">{{ formatPhoneDisplay(student.phone_number, student.nationality_code) }}</span><span v-else>未填写</span></td><td>{{ sourceText(student.enrollment_source) }}</td><td><div class="button-row compact-actions"><button class="button ghost small" @click="editStudent(student)">编辑</button><button class="button secondary small" :disabled="placementLoadingId !== null" @click="openPlacement(student)">{{ placementLoadingId === Number(student.id) ? '读取中…' : '修改寝室/床位' }}</button><button class="button ghost small" @click="openReset(student, 'password')">重置密码</button><button class="button ghost small danger-text" @click="openReset(student, 'state')">完全重置</button></div></td></tr></tbody></table></div>
      <PaginationBar v-model:page="page" v-model:page-size="pageSize" :total="total" @change="load" />
    </section>

    <ImportWorkflowModal :open="importOpen" import-type="STUDENT" title="批量导入学生" @close="importOpen = false" @committed="importCommitted" />

    <AppModal :open="Boolean(editingStudent)" size="wide" :busy="savingStudent" @close="() => closeStudentEdit()"><div v-if="editingStudent" class="student-dialog"><header class="section-head split-title"><div><span class="eyebrow">学生资料修改</span><h3 id="student-edit-title">编辑 {{ editingStudent.student_name }}</h3><p>{{ editingStudent.student_number }}</p></div><button class="button ghost small" @click="() => closeStudentEdit()">关闭</button></header><form class="form-grid student-admin-form" @submit.prevent="saveStudent"><div class="segmented-control student-category-switch-top span-2"><button type="button" :class="{ active: studentForm.studentCategory === 'DOMESTIC' }" @click="setStudentCategory('DOMESTIC')">国内生</button><button type="button" :class="{ active: studentForm.studentCategory === 'INTERNATIONAL' }" @click="setStudentCategory('INTERNATIONAL')">国际生</button></div><div class="student-contact-fields span-2"><select v-model="studentForm.nationalityCode" class="input nationality-select" @change="syncDialCode"><option v-for="country in selectableCountries" :key="country.code" :value="country.code">{{ country.name }}</option></select><div class="phone-input-group"><PhoneDialCodeSelect v-model="phoneDialCode" /><input v-model.trim="studentForm.phoneNumber" class="input" inputmode="tel" maxlength="24" placeholder="手机号码，可留空" /></div></div><input v-model.trim="studentForm.studentNumber" class="input" required pattern="\d{12}" maxlength="12" /><input v-model.trim="studentForm.studentName" class="input" required /><select v-model="studentForm.gender" class="input"><option value="M">男生</option><option value="F">女生</option></select><select v-model.number="studentForm.majorId" class="input"><option v-for="major in enabledMajors" :key="String(major.id)" :value="Number(major.id)">{{ major.major_name }}</option></select><select v-model="studentForm.degreeLevel" class="input"><option value="">培养层次可留空</option><option value="UNDERGRADUATE">本科生</option><option value="MASTER">硕士生</option><option value="DOCTOR">博士生</option><option value="MASTER_DOCTOR">硕博生</option></select><input v-model.number="studentForm.gradeYear" class="input" type="number" min="2000" max="2100" /><div class="button-row span-2 dialog-actions"><button class="button ghost" type="button" @click="() => closeStudentEdit()">取消</button><button class="button primary" :disabled="savingStudent">{{ savingStudent ? '保存中…' : '保存修改' }}</button></div></form></div></AppModal>

    <AppModal :open="Boolean(placementTarget)" size="large" :busy="placementSaving" @close="closePlacement"><div v-if="placementTarget" class="placement-dialog"><header class="section-head split-title"><div><span class="eyebrow">住宿调整</span><h3>{{ placementTarget.studentName }} · {{ placementTarget.studentNumber }}</h3><p v-if="placementTarget.resident">当前：{{ currentResidency.building_name }} {{ currentResidency.room_number }} · {{ currentResidency.bed_code || '床位待确认' }}</p><p v-else>该学生当前未入住，可直接分配可用寝室和床位。</p></div><button class="button ghost small" @click="closePlacement">关闭</button></header><p v-if="placementBeds.length === 0" class="alert warning">当前没有符合性别、学生类别、容量和活动互斥约束的可用床位。</p><form class="form-stack" @submit.prevent="savePlacement"><label><span>筛选目标寝室</span><select v-model.number="placementRoomId" class="input" required @change="choosePlacementRoom"><option v-for="room in placementRooms" :key="room.roomId" :value="room.roomId">{{ room.label }}</option></select></label><div class="placement-scene"><RoomBedScene3D :beds="placementSceneBeds" :selected-bed-ids="placementForm.bedId ? [placementForm.bedId] : []" @select="selectPlacementBed" /><div class="placement-bed-summary"><button v-for="bed in placementRoomBeds" :key="String(bed.bed_id)" type="button" class="bed-choice-button" :class="{ selected: placementForm.bedId === Number(bed.bed_id) }" @click="placementForm.bedId = Number(bed.bed_id)"><strong>{{ bed.bed_code }}</strong><span>{{ bedTypeLabel(bed.bed_type) }}</span></button></div></div><label><span>调整原因</span><textarea v-model.trim="placementForm.reason" class="input" rows="3" minlength="2" maxlength="500" required placeholder="例如：原寝室进入维护状态"></textarea></label><div class="button-row dialog-actions"><button class="button ghost" type="button" @click="closePlacement">取消</button><button class="button primary" :disabled="!placementBeds.length || placementSaving">{{ placementSaving ? '正在保存…' : '确认调整' }}</button></div></form></div></AppModal>

    <AppModal :open="Boolean(resetTarget)" size="default" :busy="resetting" @close="closeReset"><div v-if="resetTarget" class="reset-dialog"><header class="section-head split-title"><div><span class="eyebrow">学生账号重置</span><h3>{{ resetMode === 'password' ? '重置学生密码' : '完全重置学生状态' }}</h3><p>{{ resetTarget.student_name }} · {{ resetTarget.student_number }}</p></div><button class="button ghost small" @click="closeReset">关闭</button></header><p class="reset-warning" :class="{ danger: resetMode === 'state' }">{{ resetMode === 'password' ? '仅清除登录密码，不影响寝室和选寝数据。' : '将结束在住并清除选寝、组队、偏好和登录状态，操作不可恢复。' }}</p><form class="form-stack" @submit.prevent="submitReset"><input v-if="resetMode === 'state'" v-model.trim="resetForm.confirmStudentNumber" class="input" required pattern="\d{12}" placeholder="输入学号确认" /><textarea v-model.trim="resetForm.reason" class="input" rows="3" required maxlength="500" placeholder="填写操作原因"></textarea><div class="button-row dialog-actions"><button class="button ghost" type="button" @click="closeReset">取消</button><button class="button" :class="resetMode === 'state' ? 'danger' : 'primary'" :disabled="resetting">确认</button></div></form></div></AppModal>
  </div>
</template>

<style scoped>
.data-title{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.compact-section-head{margin-bottom:12px}.master-data-grid{align-items:stretch}.master-data-card{height:590px;display:flex;flex-direction:column;overflow:hidden}.master-data-card-body{display:flex;flex:1;min-height:0;flex-direction:column}.major-card-body{gap:12px}.major-create-form{flex:0 0 auto}.major-scroll-list{flex:1;min-height:0;overflow-y:auto;padding-right:5px}.student-card-body{justify-content:stretch}.student-admin-form{grid-template-columns:repeat(2,minmax(0,1fr));height:100%;align-content:space-between;grid-auto-rows:minmax(42px,auto)}.student-category-switch-top{align-self:start}.student-submit{align-self:end}.student-contact-fields{display:grid;grid-template-columns:minmax(150px,.72fr) minmax(260px,1.28fr);gap:8px;align-items:stretch}.nationality-select{background:var(--panel,#fff)}.phone-input-group{display:grid;grid-template-columns:94px minmax(0,1fr);gap:7px;min-width:0}.phone-display-text{display:inline-flex;white-space:nowrap;font-variant-numeric:tabular-nums}.segmented-control{display:grid;grid-template-columns:1fr 1fr;gap:4px;padding:4px;border-radius:12px;background:#eef2f8}.segmented-control button{min-height:38px;border:0;border-radius:9px;color:var(--text-muted);background:transparent;cursor:pointer}.segmented-control button.active{color:#1e4e9f;background:#fff;box-shadow:0 4px 12px rgba(26,50,90,.1);font-weight:700}.student-filter-row{display:grid;grid-template-columns:minmax(220px,1fr) 170px auto;gap:10px}.compact-actions{flex-wrap:wrap}.danger-text{color:var(--danger)}.student-dialog{width:min(760px,calc(100vw - 32px));padding:24px}.placement-dialog,.reset-dialog{width:min(620px,calc(100vw - 32px));padding:24px}.dialog-actions{justify-content:flex-end}.reset-warning{padding:13px;border-radius:12px;background:#f4f8ff;color:#315f91}.reset-warning.danger{background:#fff1f2;color:#991b1b}@media(max-width:760px){.data-title{flex-direction:column}.master-data-card{height:auto;min-height:0}.student-admin-form,.student-filter-row{grid-template-columns:1fr}.student-admin-form .span-2{grid-column:auto}.student-contact-fields{grid-template-columns:1fr}.phone-input-group{grid-template-columns:94px minmax(0,1fr)}}
.placement-scene{display:grid;gap:12px}.placement-scene :deep(.room-bed-scene){min-height:430px}.placement-bed-summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(110px,1fr));gap:8px}.bed-choice-button{display:grid;gap:4px;padding:11px;border:1px solid var(--line);border-radius:12px;color:inherit;background:var(--panel,#fff);text-align:left;cursor:pointer}.bed-choice-button span{color:var(--muted);font-size:12px}.bed-choice-button.selected{border-color:var(--primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--primary) 14%,transparent)}
</style>
