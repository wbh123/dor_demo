export type PublishFlowState =
  | 'IDLE'
  | 'CREATING_DRAFT'
  | 'SAVING_SCOPE'
  | 'RUNNING_PREFLIGHT'
  | 'WAITING_CONFIRMATION'
  | 'PUBLISHING'
  | 'SUCCEEDED'
  | 'FAILED'

export const ALLOWED_TRANSITIONS: Readonly<Record<PublishFlowState, readonly PublishFlowState[]>> = {
  IDLE: ['CREATING_DRAFT', 'SAVING_SCOPE', 'RUNNING_PREFLIGHT'],
  CREATING_DRAFT: ['IDLE', 'FAILED'],
  SAVING_SCOPE: ['RUNNING_PREFLIGHT', 'SUCCEEDED', 'FAILED'],
  RUNNING_PREFLIGHT: ['WAITING_CONFIRMATION', 'IDLE', 'FAILED'],
  WAITING_CONFIRMATION: ['PUBLISHING', 'IDLE', 'FAILED'],
  PUBLISHING: ['SUCCEEDED', 'FAILED'],
  SUCCEEDED: ['IDLE'],
  FAILED: ['IDLE', 'SAVING_SCOPE', 'RUNNING_PREFLIGHT', 'PUBLISHING'],
}

const BUSY_STATES = new Set<PublishFlowState>([
  'CREATING_DRAFT',
  'SAVING_SCOPE',
  'RUNNING_PREFLIGHT',
  'PUBLISHING',
])

export function transitionPublishFlow(
  current: PublishFlowState,
  next: PublishFlowState,
): PublishFlowState {
  if (current === next) return current
  if (!ALLOWED_TRANSITIONS[current].includes(next)) {
    throw new Error(`非法批次发布状态迁移：${current} → ${next}`)
  }
  return next
}

export function isPublishFlowBusy(state: PublishFlowState): boolean {
  return BUSY_STATES.has(state)
}
