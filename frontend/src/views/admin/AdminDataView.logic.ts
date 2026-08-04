// @ts-nocheck
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ListSuccessResponse, MajorRequest, ObjectSuccessResponse } from '../../api/types'
import { countryLabel, countryOptions, domesticRegionOptions } from '../../utils/countries'
import { dialCodeForCountry, formatPhoneDisplay, normalizeInternationalPhone, splitInternationalPhone } from '../../utils/phoneCodes'
import { bedTypeLabel } from '../../utils/bedLabels'
import { useI18n } from '../../i18n'

export function useAdminDataView() {
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

  return {
    computed,
    onMounted,
    reactive,
    ref,
    api,
    countryLabel,
    countryOptions,
    domesticRegionOptions,
    dialCodeForCountry,
    formatPhoneDisplay,
    normalizeInternationalPhone,
    splitInternationalPhone,
    bedTypeLabel,
    useI18n,
    currentYear,
    majors,
    students,
    total,
    page,
    pageSize,
    keyword,
    categoryFilter,
    error,
    notice,
    noticeType,
    loading,
    editingStudent,
    savingStudent,
    importOpen,
    resetTarget,
    resetMode,
    resetting,
    placementTarget,
    placementLoadingId,
    placementSaving,
    placementRoomId,
    phoneDialCode,
    majorForm,
    resetForm,
    placementForm,
    studentForm,
    subtitle,
    translateError,
    enabledMajors,
    selectableCountries,
    placementBeds,
    placementRooms,
    placementRoomBeds,
    placementSceneBeds,
    currentResidency,
    load,
    showNotice,
    searchStudents,
    createMajor,
    toggleMajor,
    setStudentCategory,
    syncDialCode,
    studentPayload,
    saveStudent,
    fillStudentForm,
    editStudent,
    closeStudentEdit,
    resetStudentForm,
    downloadTemplate,
    importCommitted,
    openReset,
    closeReset,
    submitReset,
    openPlacement,
    closePlacement,
    savePlacement,
    choosePlacementRoom,
    selectPlacementBed,
    closePlacementAfterSave,
    categoryText,
    degreeText,
    sourceText
  }
}
