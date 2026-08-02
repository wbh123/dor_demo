import { computed, reactive } from 'vue'

export interface PlatformUser {
  userId: number
  username: string
  displayName: string
  userType: 'SYSTEM_ADMIN'
  passwordChangeRequired: boolean
}

interface PlatformSessionState {
  accessToken: string
  user: PlatformUser | null
}

const STORAGE_KEY = 'wust-dormitory-platform-session'

function load(): PlatformSessionState {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value ? JSON.parse(value) as PlatformSessionState : { accessToken: '', user: null }
  } catch {
    return { accessToken: '', user: null }
  }
}

const state = reactive<PlatformSessionState>(load())

function persist(): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
}

export function usePlatformSession() {
  const authenticated = computed(() => state.user?.userType === 'SYSTEM_ADMIN' && Boolean(state.accessToken))
  const passwordChangeRequired = computed(() => Boolean(state.user?.passwordChangeRequired))

  function setSession(accessToken: string, user: PlatformUser): void {
    state.accessToken = accessToken
    state.user = user
    persist()
  }

  function clearSession(): void {
    state.accessToken = ''
    state.user = null
    localStorage.removeItem(STORAGE_KEY)
  }

  return { state, authenticated, passwordChangeRequired, setSession, clearSession }
}
