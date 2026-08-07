import axios, { AxiosError } from 'axios'
import type { ErrorResponse } from './types'

export const TOKEN_KEY = 'wust-dormitory-token'
export const SELECTION_LEASE_TOKEN_KEY = 'wust-selection-lease-token'

export class ApiRequestError extends Error {
  readonly code: string
  readonly status: number | undefined

  constructor(message: string, code = '', status?: number) {
    super(message)
    this.name = 'ApiRequestError'
    this.code = code
    this.status = status
  }
}

export const api = axios.create({
  baseURL: '/',
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  const selectionLeaseToken = sessionStorage.getItem(SELECTION_LEASE_TOKEN_KEY)
  if (selectionLeaseToken) config.headers['X-Selection-Lease-Token'] = selectionLeaseToken
  config.headers['X-Request-Id'] = crypto.randomUUID()
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ErrorResponse>) => {
    if (error.response?.status === 401) localStorage.removeItem(TOKEN_KEY)
    const contractMessage = error.response?.data?.error?.message
    const contractCode = error.response?.data?.error?.code
    return Promise.reject(new ApiRequestError(
      friendlyValidationMessage(contractMessage) || error.message || '请求失败',
      String(contractCode ?? ''),
      error.response?.status,
    ))
  },
)

function friendlyValidationMessage(message?: string) {
  if (!message) return ''
  if (/个数必须在\s*2\s*和\s*500\s*之间/.test(message)
    || /size must be between 2 and 500/i.test(message)) {
    return '修改原因至少填写2个字符，最多500个字符。'
  }
  return message
}

export interface SseMessage { id?:string; event?:string; data:unknown }

export async function subscribeRoomEvents(
  batchId: number,
  roomId: number,
  signal: AbortSignal,
  onMessage: (message: SseMessage) => void,
): Promise<void> {
  const token = localStorage.getItem(TOKEN_KEY)
  const selectionLeaseToken = sessionStorage.getItem(SELECTION_LEASE_TOKEN_KEY)
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (selectionLeaseToken) headers['X-Selection-Lease-Token'] = selectionLeaseToken
  const response = await fetch(`/api/v1/realtime/batches/${batchId}/rooms/${roomId}`, { headers, signal })
  if (!response.ok || !response.body) throw new Error(`实时连接失败：${response.status}`)
  const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = ''
  while (!signal.aborted) {
    const { value, done } = await reader.read(); if (done) break
    buffer += decoder.decode(value, { stream: true }); const frames = buffer.split('\n\n'); buffer = frames.pop() ?? ''
    for (const frame of frames) {
      const message: SseMessage = { data: null }; const dataLines: string[] = []
      for (const line of frame.split('\n')) { if (line.startsWith('id:')) message.id=line.slice(3).trim(); if(line.startsWith('event:')) message.event=line.slice(6).trim(); if(line.startsWith('data:')) dataLines.push(line.slice(5).trim()) }
      const raw=dataLines.join('\n'); try{message.data=raw?JSON.parse(raw):null}catch{message.data=raw}; onMessage(message)
    }
  }
}