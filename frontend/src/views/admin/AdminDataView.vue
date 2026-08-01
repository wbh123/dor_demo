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

const majors = ref<DataObject[]>([])
const students = ref<DataObject[]>([])
const total = ref(0)
const keyword = ref('')
const error = ref('')
const message = ref('')
const majorForm = reactive<MajorRequest>({ majorCode: '', majorName: '', enabled: true })
const studentForm = reactive<StudentRequest>({
  studentNumber: '',
  studentName: '',
  gender: 'M',
  majorId: 0,
})

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
    error.value = reason instanceof Error ? reason.message : '基础数据加载失败'
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
    error.value = reason instanceof Error ? reason.message : '专业创建失败'
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
    error.value = reason instanceof Error ? reason.message : '专业状态修改失败'
  }
}

async function createStudent() {
  try {
    await api.post('/api/v1/admin/students', studentForm)
    studentForm.studentNumber = ''
    studentForm.studentName = ''
    message.value = '学生已创建，账号处于待激活状态。'
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '学生创建失败'
  }
}
</script>

<template>
  <div class="content-column">
    <div class="page-title">
      <span class="eyebrow">MASTER DATA</span>
      <h2>专业与学生</h2>
      <p>学生只维护学号、姓名、性别和专业编号，不保存班级、年级或学院层级。</p>
    </div>

    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-if="message" class="alert success">{{ message }}</p>

    <div class="admin-grid equal">
      <section class="panel">
        <div class="section-head"><div><span class="eyebrow">MAJORS</span><h3>专业目录</h3></div></div>
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
        <div class="section-head"><div><span class="eyebrow">NEW STUDENT</span><h3>新增学生</h3></div></div>
        <form class="form-grid" @submit.prevent="createStudent">
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
          <button class="button primary">新增学生</button>
        </form>
      </section>
    </div>

    <section class="panel">
      <div class="section-head split-title">
        <div><span class="eyebrow">STUDENTS</span><h3>学生列表 · {{ total }}人</h3></div>
        <form class="inline-form" @submit.prevent="load">
          <input v-model.trim="keyword" class="input" placeholder="按学号或姓名搜索" />
          <button class="button secondary">查询</button>
        </form>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>学号</th><th>姓名</th><th>性别</th><th>专业</th><th>账号状态</th></tr></thead>
          <tbody>
            <tr v-for="student in students" :key="String(student.id)">
              <td>{{ student.student_number }}</td>
              <td><strong>{{ student.student_name }}</strong></td>
              <td>{{ student.gender === 'M' ? '男' : '女' }}</td>
              <td>{{ student.major_code }} · {{ student.major_name }}</td>
              <td><span class="status-chip compact">{{ student.account_status }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
