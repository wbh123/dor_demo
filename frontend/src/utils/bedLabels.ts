export function bedTypeLabel(value: unknown): string {
  return ({
    LOFT_BED_DESK: '上床下桌',
    BUNK_UPPER: '上下铺-上铺',
    BUNK_LOWER: '上下铺-下铺',
    BUNK: '上下铺',
  } as Record<string, string>)[String(value ?? '')] ?? String(value ?? '未确认')
}

export function bedPreferenceLabel(value: unknown): string {
  return ({
    LOFT_BED_DESK: '偏好上床下桌',
    BUNK_UPPER: '偏好上下铺-上铺',
    BUNK_LOWER: '偏好上下铺-下铺',
    NO_PREFERENCE: '床位类型不限',
  } as Record<string, string>)[String(value ?? '')] ?? '床位类型不限'
}
