import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api, TOKEN_KEY } from '../api/client'
import { applyBusinessEntitlements } from '../composables/useFeatureAccess'
import type {
  ActivateRequest,
  CurrentUserData,
  CurrentUserSuccessResponse,
  LoginRequest,
  LoginSuccessResponse,
} from '../api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<CurrentUserData | null>(null)
  const loading = ref(false)
  const welcomeAcknowledging = ref(false)

  const authenticated = computed(() => Boolean(token.value && user.value))
  const isAdmin = computed(() => user.value?.userType === 'ADMIN')
  const isStudent = computed(() => user.value?.userType === 'STUDENT')
  const welcomeRequired = computed(() =>
    Boolean(isStudent.value && user.value?.welcome?.required),
  )

  async function login(payload: LoginRequest) {
    loading.value = true
    try {
      const response = await api.post<LoginSuccessResponse>('/api/v1/auth/login', payload)
      const data = response.data.data
      if (!data?.accessToken || !data.user) throw new Error('登录响应不完整')
      token.value = data.accessToken
      setUser(data.user)
      localStorage.setItem(TOKEN_KEY, data.accessToken)
    } finally {
      loading.value = false
    }
  }

  async function activate(payload: ActivateRequest) {
    loading.value = true
    try {
      await api.post('/api/v1/auth/activate', payload)
    } finally {
      loading.value = false
    }
  }

  async function restore() {
    if (!token.value) return
    try {
      const response = await api.get<CurrentUserSuccessResponse>('/api/v1/auth/me')
      setUser(response.data.data ?? null)
    } catch {
      clear()
    }
  }

  async function acknowledgeWelcome() {
    if (!welcomeRequired.value || welcomeAcknowledging.value) return
    welcomeAcknowledging.value = true
    try {
      await api.post('/api/v1/auth/welcome/acknowledge')
      if (user.value?.welcome) {
        user.value.welcome.required = false
      }
    } finally {
      welcomeAcknowledging.value = false
    }
  }

  async function logout() {
    try {
      if (token.value) await api.post('/api/v1/auth/logout')
    } finally {
      clear()
    }
  }

  function setUser(current: CurrentUserData | null) {
    user.value = current
    applyBusinessEntitlements({ features: current?.features ?? [] })
  }

  function clear() {
    token.value = null
    setUser(null)
    localStorage.removeItem(TOKEN_KEY)
  }

  return {
    token,
    user,
    loading,
    welcomeAcknowledging,
    authenticated,
    isAdmin,
    isStudent,
    welcomeRequired,
    login,
    activate,
    restore,
    acknowledgeWelcome,
    logout,
    clear,
  }
})
