import { usePlatformSession } from './session'

const API_BASE = '/api/v1/platform'

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const { state, clearSession } = usePlatformSession()
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  if (state.accessToken) headers.set('Authorization', `Bearer ${state.accessToken}`)
  const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  if (response.status === 401) clearSession()
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string; code?: string }
    throw new Error(body.message || body.code || `请求失败（${response.status}）`)
  }
  if (response.status === 204) return undefined as T
  return await response.json() as T
}

export interface PlatformLoginResponse {
  accessToken: string
  expiresInSeconds: number
  user: import('./session').PlatformUser
}

export const platformApi = {
  login: (username: string, password: string) => request<PlatformLoginResponse>('/login', {
    method: 'POST', body: JSON.stringify({ username, password }),
  }),
  me: () => request<import('./session').PlatformUser>('/me'),
  changePassword: (currentPassword: string, newPassword: string) => request<{ changed: boolean; reloginRequired: boolean }>('/password', {
    method: 'POST', body: JSON.stringify({ currentPassword, newPassword }),
  }),
  plans: () => request<Record<string, unknown>[]>('/plans'),
  planRevision: (id: number) => request<Record<string, unknown>>(`/plans/revisions/${id}`),
  createPlan: (payload: Record<string, unknown>) => request<{ revisionId: number }>('/plans', {
    method: 'POST', body: JSON.stringify(payload),
  }),
  revisePlan: (sourceRevisionId: number, payload: Record<string, unknown>) => request<{ revisionId: number }>(`/plans/revisions/${sourceRevisionId}`, {
    method: 'POST', body: JSON.stringify(payload),
  }),
  subscription: () => request<Record<string, unknown>>('/subscription'),
  subscriptionHistory: () => request<Record<string, unknown>[]>('/subscription/history'),
  previewChange: (targetPlanRevisionId: number) => request<Record<string, unknown>>(`/subscription/preview?targetPlanRevisionId=${targetPlanRevisionId}`),
  changePlan: (payload: Record<string, unknown>) => request<Record<string, unknown>>('/subscription/plan', {
    method: 'POST', body: JSON.stringify(payload),
  }),
  changeStatus: (action: string, reason: string) => request<Record<string, unknown>>('/subscription/status', {
    method: 'POST', body: JSON.stringify({ action, reason }),
  }),
  features: () => request<Record<string, unknown>[]>('/features'),
  featureOverrides: () => request<Record<string, unknown>[]>('/feature-overrides'),
  addFeatureOverride: (payload: Record<string, unknown>) => request<{ id: number }>('/feature-overrides', {
    method: 'POST', body: JSON.stringify(payload),
  }),
  quotas: () => request<Record<string, unknown>>('/quotas'),
  addQuotaOverride: (payload: Record<string, unknown>) => request<{ id: number }>('/quota-overrides', {
    method: 'POST', body: JSON.stringify(payload),
  }),
  audit: (limit = 100) => request<Record<string, unknown>[]>(`/audit?limit=${limit}`),
}
