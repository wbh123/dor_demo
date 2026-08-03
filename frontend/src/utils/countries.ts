export interface CountryOption {
  code: string
  name: string
}

const COUNTRY_CODES = [
  'CN','HK','MO','TW','JP','KR','KP','SG','MY','TH','VN','LA','KH','MM','PH','ID','BN','TL',
  'IN','PK','BD','LK','NP','BT','MV','MN','KZ','UZ','TM','KG','TJ','AF','IR','IQ','SA','AE','QA','KW','BH','OM','YE','JO','IL','PS','LB','SY','TR',
  'RU','UA','BY','PL','DE','FR','IT','ES','PT','NL','BE','LU','CH','AT','CZ','SK','HU','RO','BG','GR','CY','MT','HR','SI','RS','BA','ME','MK','AL','MD','LT','LV','EE','FI','SE','NO','DK','IS','IE','GB',
  'US','CA','MX','BR','AR','CL','PE','CO','VE','EC','BO','PY','UY','GY','SR','CU','JM','HT','DO','CR','PA','GT','HN','SV','NI','BZ','BS','TT',
  'AU','NZ','PG','FJ','WS','TO','VU','SB','NR','KI','TV','FM','MH','PW',
  'ZA','EG','MA','DZ','TN','LY','SD','SS','ET','ER','DJ','SO','KE','UG','TZ','RW','BI','CD','CG','GA','CM','NG','GH','CI','SN','ML','NE','TD','BF','BJ','TG','GM','GN','GW','SL','LR','MR','CV','GQ','ST','AO','ZM','ZW','BW','NA','MZ','MG','MU','SC','KM','LS','SZ','MW'
] as const

const DISPLAY_NAMES = typeof Intl !== 'undefined' && 'DisplayNames' in Intl
  ? new Intl.DisplayNames(['zh-CN'], { type: 'region' })
  : null

export const countryOptions: CountryOption[] = COUNTRY_CODES
  .map((code) => ({ code, name: DISPLAY_NAMES?.of(code) ?? code }))
  .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))

export function countryLabel(code: unknown): string {
  const normalized = String(code ?? '').trim().toUpperCase()
  if (!normalized) return '未填写'
  return DISPLAY_NAMES?.of(normalized) ?? normalized
}
