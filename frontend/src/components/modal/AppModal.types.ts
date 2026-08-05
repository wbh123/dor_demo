export type AppModalSize = 'default' | 'wide' | 'large' | 'fullscreen'
export type ConfirmDialogVariant = 'default' | 'warning' | 'danger'

export interface ConfirmDialogPayload {
  reason: string
  confirmationWord: string
}
