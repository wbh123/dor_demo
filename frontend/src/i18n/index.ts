import { computed, ref, watch } from 'vue'

export type LocaleCode = 'zh-CN' | 'en-US'

const LOCALE_KEY = 'wust-dormitory-locale'
const MANUAL_LOCALE_KEY = 'wust-dormitory-locale-manual'

export const localeOptions: Array<{ value: LocaleCode; label: string }> = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' },
]

export const countryLanguageMap: Record<string, LocaleCode> = {
  CN: 'zh-CN',
  TW: 'zh-CN',
  HK: 'zh-CN',
  MO: 'zh-CN',
  US: 'en-US',
  GB: 'en-US',
  CA: 'en-US',
  AU: 'en-US',
  NZ: 'en-US',
  IE: 'en-US',
  SG: 'en-US',
  IN: 'en-US',
  ZA: 'en-US',
}

const supported = new Set<LocaleCode>(localeOptions.map((item) => item.value))
const browserLocale: LocaleCode = navigator.language.toLowerCase().startsWith('zh')
  ? 'zh-CN'
  : 'en-US'
const storedLocale = localStorage.getItem(LOCALE_KEY) as LocaleCode | null
const locale = ref<LocaleCode>(storedLocale && supported.has(storedLocale) ? storedLocale : browserLocale)

const messages: Record<LocaleCode, Record<string, string>> = {
  'zh-CN': {
    'common.confirm': '确认',
    'common.cancel': '取消',
    'common.close': '关闭',
    'common.save': '保存',
    'common.loading': '正在加载…',
    'common.accept': '接受',
    'common.reject': '拒绝',
    'common.dismiss': '暂不确认',
    'common.leave': '退出',
    'common.remove': '移除',
    'common.empty': '暂无数据',
    'language.label': '语言',
    'welcome.title': '新同学，欢迎你',
    'welcome.start': '开始使用',
    'team.invitation.title': '你收到一条组队邀请',
    'team.invitation.message': '{name} 邀请你加入选寝小组。你可以现在确认，也可以暂时关闭后到组队页面处理。',
    'team.pendingInvalidation.title': '未确认邀请将失效',
    'team.pendingInvalidation.message': '进入组队选寝后，仅包含已接受邀请的成员，其余未确认邀请会立即失效。',
    'team.personalExit.title': '进入个人选寝将退出当前队伍',
    'team.personalExit.message': '确认后你将退出当前队伍；如果你是邀请发起人，队伍会解散。',
    'team.leave.title': '确认退出队伍',
    'team.remove.title': '确认移除队友',
    'team.maxHint': '每组最多5人，邀请发起人最多邀请4名队友。',
    'notification.teamRemoved.title': '你已被移出选寝小组',
    'notification.teamRemoved.message': '{leaderName} 已将你移出选寝小组，你可以加入其他队伍或进行个人选寝。',
    'notification.teamDissolved.title': '选寝小组已解散',
    'notification.teamDissolved.message': '邀请发起人 {leaderName} 已退出，小组已自动解散。',
    'notification.invitationCancelled.title': '组队邀请已失效',
    'notification.invitationCancelled.message': '邀请人已经开始选寝或解散队伍，该邀请不再有效。',
    'profile.nationality': '国籍',
    'profile.phone': '手机号码',
    'profile.phoneEdit': '修改手机号码',
    'profile.phoneSave': '保存手机号码',
    'profile.phoneEmpty': '暂未填写',
    'profile.foreignStudent': '国际学生',
    'layout.loft': '上床下桌',
    'layout.bunk': '上下铺',
    'layout.bunkHint': '切换为上下铺会新增一个独立下铺床位，房间容量增加1人。',
    'layout.maximum': '最多8人',
  },
  'en-US': {
    'common.confirm': 'Confirm',
    'common.cancel': 'Cancel',
    'common.close': 'Close',
    'common.save': 'Save',
    'common.loading': 'Loading…',
    'common.accept': 'Accept',
    'common.reject': 'Decline',
    'common.dismiss': 'Decide later',
    'common.leave': 'Leave',
    'common.remove': 'Remove',
    'common.empty': 'No data',
    'language.label': 'Language',
    'welcome.title': 'Welcome, new student',
    'welcome.start': 'Get started',
    'team.invitation.title': 'You have a team invitation',
    'team.invitation.message': '{name} invited you to a dormitory selection team. You can respond now or close this dialog and decide on the team page.',
    'team.pendingInvalidation.title': 'Pending invitations will expire',
    'team.pendingInvalidation.message': 'Only members who accepted the invitation will be included. All pending invitations will be cancelled when team selection starts.',
    'team.personalExit.title': 'Personal selection will leave your team',
    'team.personalExit.message': 'After confirmation, you will leave the current team. If you are the inviter, the team will be dissolved.',
    'team.leave.title': 'Leave this team?',
    'team.remove.title': 'Remove this member?',
    'team.maxHint': 'A team can contain up to five students, including the inviter and four invitees.',
    'notification.teamRemoved.title': 'You were removed from the team',
    'notification.teamRemoved.message': '{leaderName} removed you from the dormitory selection team. You may join another team or select a bed individually.',
    'notification.teamDissolved.title': 'The team was dissolved',
    'notification.teamDissolved.message': 'The inviter, {leaderName}, left the team, so the team was dissolved.',
    'notification.invitationCancelled.title': 'The invitation has expired',
    'notification.invitationCancelled.message': 'The inviter started selection or dissolved the team, so this invitation is no longer valid.',
    'profile.nationality': 'Nationality',
    'profile.phone': 'Mobile number',
    'profile.phoneEdit': 'Edit mobile number',
    'profile.phoneSave': 'Save mobile number',
    'profile.phoneEmpty': 'Not provided',
    'profile.foreignStudent': 'International student',
    'layout.loft': 'Loft bed with desk',
    'layout.bunk': 'Bunk bed',
    'layout.bunkHint': 'Changing to a bunk bed creates a separate lower-bed option and increases room capacity by one.',
    'layout.maximum': 'Maximum 8 residents',
  },
}

const textTranslations: Record<string, string> = {
  登录: 'Sign in',
  学生激活: 'Student activation',
  退出登录: 'Sign out',
  工作台: 'Dashboard',
  专业与学生: 'Majors and students',
  宿舍资源: 'Dormitory resources',
  匹配规则: 'Matching rules',
  选寝批次: 'Selection batches',
  分配与调整: 'Assignments',
  选寝首页: 'Selection home',
  我的小组: 'My team',
  我的队伍: 'My team',
  个人偏好: 'Personal preferences',
  选择宿舍和床位: 'Choose room and bed',
  组队选寝: 'Team selection',
  修改个人偏好: 'Edit preferences',
  填写个人偏好: 'Complete preferences',
  我的住宿结果: 'My accommodation',
  '尚未确定宿舍和床位。': 'No room or bed has been selected yet.',
  选择宿舍房间: 'Choose a room',
  搜索房间: 'Search rooms',
  筛选楼层: 'Floor',
  最少剩余铺位: 'Minimum available beds',
  全部楼层: 'All floors',
  不限: 'Any',
  查看床位布局: 'View bed layout',
  返回房间列表: 'Back to room list',
  当前选择: 'Current selection',
  尚未选择: 'Not selected',
  临时保留: 'Temporary hold',
  尚未临时保留: 'No active hold',
  主动释放: 'Release',
  确认当前床位: 'Confirm bed',
  可选择: 'Available',
  已选中: 'Selected',
  暂时保留: 'Held',
  已有同学选择: 'Occupied',
  保存个人偏好: 'Save preferences',
  返回首页: 'Back to home',
  邀请同学: 'Invite student',
  接受邀请: 'Accept invitation',
  拒绝邀请: 'Decline invitation',
  开始组队选寝: 'Start team selection',
  退出队伍: 'Leave team',
  删除队友: 'Remove member',
  新生欢迎语: 'New-student welcome messages',
  保存欢迎语: 'Save welcome messages',
  中文欢迎语: 'Chinese welcome message',
  英文欢迎语: 'English welcome message',
  国籍: 'Nationality',
  手机号码: 'Mobile number',
  男寝: 'Male dormitory',
  女寝: 'Female dormitory',
  管理控制台: 'Administration console',
  刷新数据: 'Refresh',
  保存布局: 'Save layout',
  保存类型与布局: 'Save type and layout',
  恢复默认布局: 'Restore default layout',
  上床下桌: 'Loft bed with desk',
  上下铺: 'Bunk bed',
  '顺时针旋转90°': 'Rotate 90° clockwise',
  拖动调整位置: 'Drag to reposition',
  '非空床位·类型锁定': 'Occupied · type locked',
  当前房型: 'Current room type',
  同步房型: 'Updated room type',
  同步容量: 'Updated capacity',
  修改原因: 'Reason for change',
  选寝批次与统一分配: 'Selection batches and allocation',
  准备全部学生与宿舍范围: 'Prepare all students and dormitory scope',
  预演统一分配: 'Preview allocation',
  统一分配预演: 'Allocation preview',
  统一分配执行结果: 'Allocation result',
  待分配学生: 'Students to allocate',
  可用床位: 'Available beds',
  预计成功: 'Expected assignments',
  无法分配: 'Unassigned',
  未分配学生清单: 'Unassigned student list',
  失败代码: 'Failure code',
  失败原因: 'Failure reason',
  确认正式执行: 'Commit allocation',
  重置密码: 'Reset password',
  完全重置: 'Full reset',
  重置学生密码: 'Reset student password',
  完全重置学生状态: 'Fully reset student state',
  仅重置登录信息: 'Reset sign-in data only',
  不可恢复的完整重置: 'Irreversible full reset',
  输入学号确认: 'Enter student number to confirm',
  操作原因: 'Reason',
  确认完全重置: 'Confirm full reset',
  确认重置密码: 'Confirm password reset',
  正在处理: 'Processing',
}

const subtitleTranslations: Record<string, string> = {
  'WELCOME BACK': '欢迎回来',
  'MY DORMITORY': '我的住宿结果',
  'PERSONAL PREFERENCES': '个人偏好',
  'ROOM MATCHING': '宿舍匹配',
  'ROOM LAYOUT': '床位布局',
  'TEAM SELECTION': '组队选寝',
  'OPERATIONS OVERVIEW': '运行概览',
  'FIRST LOGIN WELCOME': '首次登录欢迎',
  'MATCHING OPERATIONS': '匹配规则',
  'WUST DORMITORY SELECT': '高校选寝',
  'SELECTION OPERATIONS': '选寝批次与统一分配',
  'ALLOCATION PREVIEW': '统一分配预演',
  'FAILED STUDENTS': '未分配学生',
  'STUDENT ACCOUNT RESET': '学生账号重置',
}

const originalText = new WeakMap<Node, string>()
const originalAttributes = new WeakMap<Element, Map<string, string>>()
const observerOptions: MutationObserverInit = {
  childList: true,
  subtree: true,
  characterData: true,
}
let observer: MutationObserver | null = null

function interpolate(template: string, params: Record<string, unknown> = {}) {
  return template.replace(/\{(\w+)\}/g, (_, key: string) => String(params[key] ?? ''))
}

function t(key: string, params: Record<string, unknown> = {}) {
  const template = messages[locale.value][key] ?? messages['zh-CN'][key] ?? key
  return interpolate(template, params)
}

function subtitle(chinese: string, english: string) {
  return locale.value === 'zh-CN' ? english : chinese
}

function countryName(code: unknown) {
  const value = String(code ?? 'CN').toUpperCase()
  const displayLocale = locale.value === 'zh-CN' ? 'zh-CN' : 'en-US'
  try {
    return new Intl.DisplayNames([displayLocale], { type: 'region' }).of(value) ?? value
  } catch {
    return value
  }
}

function setLocale(next: LocaleCode, manual = true) {
  if (!supported.has(next)) return
  locale.value = next
  localStorage.setItem(LOCALE_KEY, next)
  if (manual) localStorage.setItem(MANUAL_LOCALE_KEY, '1')
}

function applyNationalityLocale(nationalityCode: unknown) {
  if (localStorage.getItem(MANUAL_LOCALE_KEY) === '1') return
  const country = String(nationalityCode ?? 'CN').toUpperCase()
  setLocale(countryLanguageMap[country] ?? 'en-US', false)
}

function welcomeMessage(value: unknown) {
  const source = (value ?? {}) as Record<string, unknown>
  const selected = source[locale.value] ?? source['en-US'] ?? source['zh-CN']
  return selected ? String(selected) : ''
}

function translateError(reason: unknown) {
  const value = reason as { response?: { data?: { error?: { code?: string; message?: string } } }; message?: string }
  const code = value?.response?.data?.error?.code
  const errorKey = code ? `error.${code}` : ''
  if (errorKey && messages[locale.value][errorKey]) return t(errorKey)
  return value?.response?.data?.error?.message ?? value?.message ?? t('common.empty')
}

function translateTextValue(value: string, element?: Element) {
  const trimmed = value.trim()
  if (!trimmed) return value
  if (element?.classList.contains('eyebrow')) {
    if (locale.value === 'zh-CN') return value
    return value.replace(trimmed, subtitleTranslations[trimmed] ?? trimmed)
  }
  if (locale.value === 'zh-CN') return value
  const translated = textTranslations[trimmed]
  return translated ? value.replace(trimmed, translated) : value
}

function pauseObserver() {
  observer?.disconnect()
}

function resumeObserver() {
  if (!observer || !document.body) return
  observer.observe(document.body, observerOptions)
}

function translateNode(node: Node) {
  if (node.nodeType === Node.TEXT_NODE) {
    const current = node.textContent ?? ''
    const parent = node.parentElement
    if (!originalText.has(node)) originalText.set(node, current)
    const source = originalText.get(node) ?? ''
    const translated = translateTextValue(source, parent ?? undefined)
    if (translated === current) return
    node.textContent = translated
    return
  }
  if (!(node instanceof Element)) return
  const attributeNames = ['placeholder', 'title', 'aria-label']
  let values = originalAttributes.get(node)
  if (!values) {
    values = new Map<string, string>()
    originalAttributes.set(node, values)
  }
  for (const name of attributeNames) {
    const current = node.getAttribute(name)
    if (current !== null && !values.has(name)) values.set(name, current)
    const source = values.get(name)
    if (source === undefined) continue
    const translated = translateTextValue(source, node)
    if (translated !== current) node.setAttribute(name, translated)
  }
  node.childNodes.forEach(translateNode)
}

function refreshDomTranslations() {
  if (!document.body) return
  pauseObserver()
  try {
    translateNode(document.body)
  } finally {
    resumeObserver()
  }
}

function installDomI18n() {
  if (observer) return
  observer = new MutationObserver((mutations) => {
    pauseObserver()
    try {
      for (const mutation of mutations) {
        if (mutation.type === 'characterData') {
          originalText.set(mutation.target, mutation.target.textContent ?? '')
          translateNode(mutation.target)
          continue
        }
        mutation.addedNodes.forEach(translateNode)
      }
    } finally {
      resumeObserver()
    }
  })
  refreshDomTranslations()
}

watch(locale, () => {
  document.documentElement.lang = locale.value
  queueMicrotask(refreshDomTranslations)
}, { immediate: true })

export function useI18n() {
  return {
    locale,
    localeOptions,
    isChinese: computed(() => locale.value === 'zh-CN'),
    t,
    subtitle,
    countryName,
    setLocale,
    applyNationalityLocale,
    welcomeMessage,
    translateError,
    installDomI18n,
  }
}
