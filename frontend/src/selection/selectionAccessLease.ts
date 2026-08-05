import { api } from '../api/client'
import type { DataObject, ObjectSuccessResponse } from '../api/types'

export const SELECTION_LEASE_TOKEN_KEY = 'wust-selection-lease-token'

let heartbeat: number | null = null
let starting: Promise<void> | null = null
let pageHideInstalled = false

function token() {
  return sessionStorage.getItem(SELECTION_LEASE_TOKEN_KEY) ?? ''
}

export async function startSelectionAccessLease(): Promise<void> {
  if (heartbeat !== null || starting) return starting ?? Promise.resolve()
  starting = acquire()
  try {
    await starting
  } finally {
    starting = null
  }
}

export async function stopSelectionAccessLease(): Promise<void> {
  if (heartbeat !== null) {
    window.clearInterval(heartbeat)
    heartbeat = null
  }
  const current = token()
  sessionStorage.removeItem(SELECTION_LEASE_TOKEN_KEY)
  if (!current) return
  try {
    await api.delete(`/api/v1/student/selection-leases/${encodeURIComponent(current)}`)
  } catch {
    // 租约自身会在超时后自动释放，离开页面时无需阻塞导航。
  }
}

async function acquire() {
  const response = await api.post<ObjectSuccessResponse>('/api/v1/student/selection-leases')
  const data = (response.data.data ?? {}) as DataObject
  if (!Boolean(data.limited)) {
    sessionStorage.removeItem(SELECTION_LEASE_TOKEN_KEY)
    return
  }
  const leaseToken = String(data.token ?? '')
  if (!leaseToken) throw new Error('并发控制服务未返回选寝访问凭证')
  sessionStorage.setItem(SELECTION_LEASE_TOKEN_KEY, leaseToken)
  const heartbeatSeconds = Math.max(10, Number(data.heartbeatSeconds ?? 25))
  heartbeat = window.setInterval(() => { void renew() }, heartbeatSeconds * 1000)
  installPageHideHandler()
}

async function renew() {
  const current = token()
  if (!current) return
  try {
    const response = await api.put<ObjectSuccessResponse>(
      `/api/v1/student/selection-leases/${encodeURIComponent(current)}`,
    )
    const data = (response.data.data ?? {}) as DataObject
    if (!Boolean(data.limited)) {
      await stopSelectionAccessLease()
    }
  } catch {
    sessionStorage.removeItem(SELECTION_LEASE_TOKEN_KEY)
    if (heartbeat !== null) {
      window.clearInterval(heartbeat)
      heartbeat = null
    }
  }
}

function installPageHideHandler() {
  if (pageHideInstalled) return
  pageHideInstalled = true
  window.addEventListener('pagehide', () => {
    const current = token()
    if (!current) return
    sessionStorage.removeItem(SELECTION_LEASE_TOKEN_KEY)
    const auth = localStorage.getItem('wust-dormitory-token')
    void fetch(`/api/v1/student/selection-leases/${encodeURIComponent(current)}`, {
      method: 'DELETE',
      keepalive: true,
      headers: auth ? { Authorization: `Bearer ${auth}` } : undefined,
    })
  })
}
