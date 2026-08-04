import { countryOptions } from './countries'

const DIAL_CODES: Record<string, string> = {
  AF:'93',AL:'355',DZ:'213',AO:'244',AR:'54',AU:'61',AT:'43',BH:'973',BD:'880',BY:'375',BE:'32',BZ:'501',BJ:'229',BT:'975',BO:'591',BA:'387',BW:'267',BR:'55',BN:'673',BG:'359',BF:'226',BI:'257',KH:'855',CM:'237',CA:'1',CV:'238',TD:'235',CL:'56',CN:'86',CO:'57',KM:'269',CG:'242',CD:'243',CR:'506',CI:'225',HR:'385',CU:'53',CY:'357',CZ:'420',DK:'45',DJ:'253',DO:'1',EC:'593',EG:'20',SV:'503',GQ:'240',ER:'291',EE:'372',SZ:'268',ET:'251',FJ:'679',FI:'358',FR:'33',GA:'241',GM:'220',GE:'995',DE:'49',GH:'233',GR:'30',GT:'502',GN:'224',GW:'245',GY:'592',HT:'509',HN:'504',HK:'852',HU:'36',IS:'354',IN:'91',ID:'62',IR:'98',IQ:'964',IE:'353',IL:'972',IT:'39',JM:'1',JP:'81',JO:'962',KZ:'7',KE:'254',KI:'686',KP:'850',KR:'82',KW:'965',KG:'996',LA:'856',LV:'371',LB:'961',LS:'266',LR:'231',LY:'218',LT:'370',LU:'352',MG:'261',MW:'265',MY:'60',MV:'960',ML:'223',MT:'356',MH:'692',MR:'222',MU:'230',MX:'52',FM:'691',MD:'373',MN:'976',ME:'382',MA:'212',MZ:'258',MM:'95',NA:'264',NR:'674',NP:'977',NL:'31',NZ:'64',NI:'505',NE:'227',NG:'234',MK:'389',NO:'47',OM:'968',PK:'92',PW:'680',PS:'970',PA:'507',PG:'675',PY:'595',PE:'51',PH:'63',PL:'48',PT:'351',QA:'974',RO:'40',RU:'7',RW:'250',WS:'685',SA:'966',SN:'221',RS:'381',SC:'248',SL:'232',SG:'65',SK:'421',SI:'386',SB:'677',SO:'252',ZA:'27',SS:'211',ES:'34',LK:'94',SD:'249',SR:'597',SE:'46',CH:'41',SY:'963',TW:'886',TJ:'992',TZ:'255',TH:'66',TL:'670',TG:'228',TO:'676',TT:'1',TN:'216',TR:'90',TM:'993',TV:'688',UG:'256',UA:'380',AE:'971',GB:'44',US:'1',UY:'598',UZ:'998',VU:'678',VE:'58',VN:'84',YE:'967',ZM:'260',ZW:'263',BS:'1'
}

export interface PhoneCodeOption {
  countryCode: string
  countryName: string
  dialCode: string
  label: string
}

export const phoneCodeOptions: PhoneCodeOption[] = countryOptions
  .map((country) => ({
    countryCode: country.code,
    countryName: country.name,
    dialCode: DIAL_CODES[country.code] ? `+${DIAL_CODES[country.code]}` : '',
    label: `${country.name} +${DIAL_CODES[country.code] ?? ''}`,
  }))
  .filter((item) => item.dialCode)
  .sort((left, right) => left.countryName.localeCompare(right.countryName, 'zh-CN'))

export function dialCodeForCountry(countryCode: unknown): string {
  return `+${DIAL_CODES[String(countryCode ?? 'CN').toUpperCase()] ?? '86'}`
}

export function normalizeInternationalPhone(dialCode: string, localNumber: string): string | undefined {
  const digits = localNumber.replace(/\D/g, '')
  if (!digits) return undefined
  const countryDigits = dialCode.replace(/\D/g, '')
  const normalizedLocal = digits.startsWith(countryDigits) && localNumber.trim().startsWith('+')
    ? digits.slice(countryDigits.length)
    : digits.replace(/^0+/, '')
  return `+${countryDigits}${normalizedLocal}`
}

export function splitInternationalPhone(value: unknown, nationalityCode: unknown) {
  const source = String(value ?? '').trim()
  const fallback = dialCodeForCountry(nationalityCode)
  if (!source) return { dialCode: fallback, localNumber: '' }
  const digits = source.replace(/\D/g, '')
  if (!source.startsWith('+')) return { dialCode: fallback, localNumber: digits }
  const match = [...new Set(Object.values(DIAL_CODES))]
    .sort((left, right) => right.length - left.length)
    .find((code) => digits.startsWith(code))
  if (!match) return { dialCode: fallback, localNumber: digits }
  return { dialCode: `+${match}`, localNumber: digits.slice(match.length) }
}
export interface PhoneDisplayParts {
  dialCode: string
  localNumber: string
  formattedLocalNumber: string
}

function groupDigits(digits: string, groups: number[]): string {
  const result: string[] = []
  let cursor = 0
  for (const size of groups) {
    if (cursor >= digits.length) break
    result.push(digits.slice(cursor, cursor + size))
    cursor += size
  }
  if (cursor < digits.length) result.push(digits.slice(cursor))
  return result.filter(Boolean).join(' ')
}

function formatLocalNumber(dialCode: string, digits: string): string {
  if (!digits) return ''
  if (dialCode === '+86' && digits.length === 11) return groupDigits(digits, [3, 4, 4])
  if (dialCode === '+1' && digits.length === 10) return groupDigits(digits, [3, 3, 4])
  if (dialCode === '+81' && (digits.length === 9 || digits.length === 10)) {
    return digits.length === 10 ? groupDigits(digits, [3, 3, 4]) : groupDigits(digits, [2, 3, 4])
  }
  if (dialCode === '+82' && (digits.length === 9 || digits.length === 10)) {
    return digits.length === 10 ? groupDigits(digits, [3, 3, 4]) : groupDigits(digits, [2, 3, 4])
  }
  if (dialCode === '+44' && digits.length === 10) return groupDigits(digits, [4, 3, 3])
  if (digits.length <= 6) return digits
  const groups: string[] = []
  for (let index = 0; index < digits.length; index += 3) groups.push(digits.slice(index, index + 3))
  return groups.join(' ')
}

export function phoneDisplayParts(value: unknown, nationalityCode: unknown): PhoneDisplayParts {
  const split = splitInternationalPhone(value, nationalityCode)
  const localNumber = split.localNumber.replace(/\D/g, '')
  return {
    dialCode: split.dialCode,
    localNumber,
    formattedLocalNumber: formatLocalNumber(split.dialCode, localNumber),
  }
}

export function formatPhoneDisplay(value: unknown, nationalityCode: unknown): string {
  const parts = phoneDisplayParts(value, nationalityCode)
  if (!parts.localNumber) return ''
  return `${parts.dialCode} ${parts.formattedLocalNumber}`.trim()
}
