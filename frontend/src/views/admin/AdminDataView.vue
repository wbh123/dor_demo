<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../../api/client'
import type {
  DataObject,
  ListSuccessResponse,
  MajorRequest,
  ObjectSuccessResponse,
  StudentRequest,
} from '../../api/types'
import { useI18n } from '../../i18n'

const majors = ref<DataObject[]>([])
const students = ref<DataObject[]>([])
const total = ref(0)
const keyword = ref('')
const error = ref('')
const message = ref('')
const editingStudentId = ref<number | null>(null)
const resetting = ref(false)
const resetTarget = ref<DataObject | null>(null)
const resetMode = ref<'password' | 'state'>('password')
const resetForm = reactive({ reason: '', confirmStudentNumber: '' })
const majorForm = reactive<MajorRequest>({ majorCode: '', majorName: '', enabled: true })
const studentForm = reactive<StudentRequest>({
  studentNumber: '',
  studentName: '',
  gender: 'M',
  majorId: 0,
  nationalityCode: 'CN',
  phoneNumber: '',
})
const { countryName, subtitle, translateError } = useI18n()

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [majorResponse, studentResponse] = await Promise.all([
      api.get<ListSuccessResponse>('/api/v1/admin/majors'),
      api.get<ObjectSuccessResponse>('/api/v1/admin/students', {
        params: { keyword: keyword.value || undefined, page: 1, size: 100 },
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
  try {
    await api.post('/api/v1/admin/majors', majorForm)
    majorForm.majorCode = ''
    majorForm.majorName = ''
    majorForm.enabled = true
    message.value = '专业已创建。'
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  }
}

async function toggleMajor(major: DataObject) {
  try {
    await api.put(`/api/v1/admin/majors/${major.id}`, {
      majorCode: major.major_code,
      majorName: major.major_name,
      enabled: !Boolean(major.enabled),
    })
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  }
}

async function saveStudent() {
  error.value = ''
  message.value = ''
  try {
    const payload = {
      ...studentForm,
      nationalityCode: String(studentForm.nationalityCode || 'CN').trim().toUpperCase(),
      phoneNumber: String(studentForm.phoneNumber || '').trim() || undefined,
    }
    if (editingStudentId.value) {
      await api.put(`/api/v1/admin/students/${editingStudentId.value}`, payload)
      message.value = '学生资料已更新。'
    } else {
      await api.post('/api/v1/admin/students', payload)
      message.value = '学生已创建，账号处于待激活状态。'
    }
    resetStudentForm()
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  }
}

function editStudent(student: DataObject) {
  editingStudentId.value = Number(student.id)
  studentForm.studentNumber = String(student.student_number)
  studentForm.studentName = String(student.student_name)
  studentForm.gender = String(student.gender) as StudentRequest['gender']
  studentForm.majorId = Number(student.major_id)
  studentForm.nationalityCode = String(student.nationality_code ?? 'CN')
  studentForm.phoneNumber = String(student.phone_number ?? '')
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function resetStudentForm() {
  editingStudentId.value = null
  studentForm.studentNumber = ''
  studentForm.studentName = ''
  studentForm.gender = 'M'
  studentForm.nationalityCode = 'CN'
  studentForm.phoneNumber = ''
  if (majors.value.length) studentForm.majorId = Number(majors.value[0].id)
}

function openReset(student: DataObject, mode: 'password' | 'state') {
  resetTarget.value = student
  resetMode.value = mode
  resetForm.reason = ''
  resetForm.confirmStudentNumber = ''
  error.value = ''
  message.value = ''
}

function closeReset() {
  if (resetting.value) return
  resetTarget.value = null
}

async function submitReset() {
  if (!resetTarget.value) return
  error.value = ''
  message.value = ''
  resetting.value = true
  try {
    const id = Number(resetTarget.value.id)
    if (resetMode.value === 'password') {
      await api.post(`/api/v1/admin/students/${id}/reset-password`, {
        reason: resetForm.reason.trim(),
      })
      message.value = `${resetTarget.value.student_name}的密码已重置，账号恢复为待激活。`
    } else {
      await api.post(`/api/v1/admin/students/${id}/reset-state`, {
        confirmStudentNumber: resetForm.confirmStudentNumber.trim(),
        reason: resetForm.reason.trim(),
      })
      message.value = `${resetTarget.value.student_name}的账号及全部选寝状态已完全重置。`
    }
    resetTarget.value = null
    await load()
  } catch (reason) {
    error.value = translateError(reason)
  } finally {
    resetting.value = false
  }
}
</script>

<template>
  <div class="content-column">
    <div class="page-title">
      <span class="eyebrow">{{ subtitle('基础数据', 'MASTER DATA') }}</span>
      <h2>专业与学生</h2>
      <p>学生维护12位学号、姓名、性别、专业、国籍与手机号码。学生登录后可自行修改手机号码。</p>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <div v-if="resetTarget" class="modal-overlay student-reset-overlay" @click.self="closeReset">
      <section class="modal-card student-reset-dialog" role="dialog" aria-modal="true" aria-labelledby="student-reset-title">
        <header class="section-head split-title">
          <div>
            <span class="eyebrow">STUDENT ACCOUNT RESET</span>
            <h3 id="student-reset-title">{{ resetMode === 'password' ? '重置学生密码' : '完全重置学生状态' }}</h3>
            <p>{{ resetTarget.student_name }} · {{ resetTarget.student_number }}</p>
          </div>
          <button class="button ghost small" type="button" :disabled="resetting" @click="closeReset">关闭</button>
        </header>

        <div class="student-reset-warning" :class="{ danger: resetMode === 'state' }">
          <strong>{{ resetMode === 'password' ? '仅重置登录信息' : '不可恢复的完整重置' }}</strong>
          <p v-if="resetMode === 'password'">清除密码和登录令牌，不影响床位分配、个人偏好、组队及批次资格。学生需要重新激活账号。</p>
          <p v-else>清除密码、分配结果、个人偏好、组队关系、通知和批次资格，使学生恢复到刚导入的待激活状态。</p>
        </div>

        <form class="form-stack" @submit.prevent="submitReset">
          <label v-if="resetMode === 'state'">
            <span>输入学号确认</span>
            <input
              v-model.trim="resetForm.confirmStudentNumber"
              class="input"
              required
              pattern="\d{12}"
              maxlength="12"
              :placeholder="String(resetTarget.student_number)"
            />
          </label>
          <label>
            <span>操作原因</span>
            <textarea v-model.trim="resetForm.reason" class="input" required maxlength="500" rows="3" placeholder="请说明重置原因"></textarea>
          </label>
          <div class="button-row student-reset-actions">
            <button class="button ghost" type="button" :disabled="resetting" @click="closeReset">取消</button>
            <button class="button" :class="resetMode === 'state' ? 'danger' : 'primary'" type="submit" :disabled="resetting">
              {{ resetting ? '正在处理…' : resetMode === 'state' ? '确认完全重置' : '确认重置密码' }}
            </button>
          </div>
        </form>
      </section>
    </div>

    <div class="admin-grid equal">
      <section class="panel">
        <div class="section-head"><div><span class="eyebrow">{{ subtitle('专业目录', 'MAJORS') }}</span><h3>专业目录</h3></div></div>
        <form class="form-grid" @submit.prevent="createMajor">
          <input v-model.trim="majorForm.majorCode" class="input" required maxlength="32" placeholder="专业编号" />
          <input v-model.trim="majorForm.majorName" class="input" required maxlength="128" placeholder="专业名称" />
          <button class="button primary">新增专业</button>
        </form>
        <div class="simple-list">
          <article v-for="major in majors" :key="String(major.id)">
            <div><strong>{{ major.major_name }}</strong><p>{{ major.major_code }}</p></div>
            <button class="button ghost small" @click="toggleMajor(major)">
              {{ major.enabled ? '禁用' : '启用' }}
            </button>
          </article>
        </div>
      </section>

      <section class="panel">
        <div class="section-head split-title">
          <div>
            <span class="eyebrow">{{ subtitle(editingStudentId ? '编辑学生' : '新增学生', editingStudentId ? 'EDIT STUDENT' : 'NEW STUDENT') }}</span>
            <h3>{{ editingStudentId ? '编辑学生资料' : '新增学生' }}</h3>
          </div>
          <button v-if="editingStudentId" class="button ghost small" type="button" @click="resetStudentForm">取消编辑</button>
        </div>
        <form class="form-grid student-admin-form" @submit.prevent="saveStudent">
          <input v-model.trim="studentForm.studentNumber" class="input" required pattern="\d{12}" maxlength="12" placeholder="12位学号" />
          <input v-model.trim="studentForm.studentName" class="input" required maxlength="128" placeholder="姓名" />
          <select v-model="studentForm.gender" class="input" required>
            <option value="M">男生</option>
            <option value="F">女生</option>
          </select>
          <select v-model.number="studentForm.majorId" class="input" required>
            <option v-for="major in majors.filter((item) => item.enabled)" :key="String(major.id)" :value="Number(major.id)">
              {{ major.major_code }} · {{ major.major_name }}
            </option>
          </select>
          <input v-model.trim="studentForm.nationalityCode" class="input" required pattern="[A-Za-z]{2}" maxlength="2" placeholder="国籍代码，如 CN、US" />
          <input v-model.trim="studentForm.phoneNumber" class="input" maxlength="32" placeholder="手机号码，可留空" />
          <button class="button primary span-2">{{ editingStudentId ? '保存学生资料' : '新增学生' }}</button>
        </form>
      </section>
    </div>

    <section class="panel">
      <div class="section-head split-title">
        <div><span class="eyebrow">{{ subtitle('学生列表', 'STUDENTS') }}</span><h3>学生列表 · {{ total }}人</h3></div>
        <form class="inline-form" @submit.prevent="load">
          <input v-model.trim="keyword" class="input" placeholder="按学号、姓名或手机号搜索" />
          <button class="button secondary">查询</button>
        </form>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>学号</th><th>姓名</th><th>性别</th><th>国籍</th><th>手机号码</th><th>专业</th><th>账号状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="student in students" :key="String(student.id)">
              <td>{{ student.student_number }}</td>
              <td><strong>{{ student.student_name }}</strong></td>
              <td>{{ student.gender === 'M' ? '男' : '女' }}</td>
              <td>{{ countryName(student.nationality_code) }} · {{ student.nationality_code }}</td>
              <td>{{ student.phone_number || '-' }}</td>
              <td>{{ student.major_code }} · {{ student.major_name }}</td>
              <td><span class="status-chip compact">{{ student.account_status }}</span></td>
              <td>
                <div class="button-row wrap student-row-actions">
                  <button class="button ghost small" @click="editStudent(student)">编辑</button>
                  <button class="button secondary small" @click="openReset(student, 'password')">重置密码</button>
                  <button class="button danger small" @click="openReset(student, 'state')">完全重置</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
