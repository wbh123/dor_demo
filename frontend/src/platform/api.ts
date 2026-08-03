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

export type FeatureTargetState = 'ENABLED' | 'DISABLED' | 'INHERIT'
export type FeaturePhase = 'PHASE1' | 'PHASE2' | 'PHASE3'
export type FeatureScope = 'ADMIN' | 'STUDENT' | 'SHARED'
export type FeatureRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type FeatureSource = 'PLAN_ENABLED' | 'PLAN_DISABLED' | 'OVERRIDE_GRANT' | 'OVERRIDE_REVOKE'

export interface FeatureEntitlement {
  featureCode: string
  featureName: string
  phase: FeaturePhase
  scope: FeatureScope
  granularity: 'MODULE' | 'OPERATION'
  actionType: string
  riskLevel: FeatureRiskLevel
  enabledInProgram: boolean
  sortOrder: number
  planEnabled: boolean
  effectiveEnabled: boolean
  overrideType: 'GRANT' | 'REVOKE' | null
  source: FeatureSource
  lastChangedAt: string | null
}

export interface FeatureStateChange {
  featureCode: string
  targetState: FeatureTargetState
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
  featureEntitlements: (includeFuture = false) => request<FeatureEntitlement[]>(`/features/entitlements?includeFuture=${includeFuture}`),
  setFeatureState: (featureCode: string, targetState: FeatureTargetState, reason: string) => request<FeatureEntitlement>(`/features/${encodeURIComponent(featureCode)}/state`, {
    method: 'PUT', body: JSON.stringify({ targetState, reason }),
  }),
  setFeatureStates: (changes: FeatureStateChange[], reason: string) => request<FeatureEntitlement[]>('/features/batch-state', {
    method: 'POST', body: JSON.stringify({ changes, reason }),
  }),
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
