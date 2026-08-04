import { computed, ref, watch } from 'vue'
import { messages as zhMessages, textTranslations as zhTextTranslations } from './locales/zh-CN'
import { messages as enMessages, subtitleTranslations, textTranslations as enTextTranslations } from './locales/en-US'

export type LocaleCode = 'zh-CN' | 'en-US'

const LOCALE_KEY = 'wust-dormitory-locale'
const MANUAL_LOCALE_KEY = 'wust-dormitory-locale-manual'

export const localeOptions: Array<{ value: LocaleCode; label: string }> = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' },
]

export const countryLanguageMap: Record<string, LocaleCode> = {
  CN: 'zh-CN', TW: 'zh-CN', HK: 'zh-CN', MO: 'zh-CN',
  US: 'en-US', GB: 'en-US', CA: 'en-US', AU: 'en-US', NZ: 'en-US',
  IE: 'en-US', SG: 'en-US', IN: 'en-US', ZA: 'en-US',
}

const supported = new Set<LocaleCode>(localeOptions.map((item) => item.value))
const browserLocale: LocaleCode = navigator.language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
const storedLocale = localStorage.getItem(LOCALE_KEY) as LocaleCode | null
const locale = ref<LocaleCode>(storedLocale && supported.has(storedLocale) ? storedLocale : browserLocale)
const messages: Record<LocaleCode, Record<string, string>> = {
  'zh-CN': zhMessages,
  'en-US': enMessages,
}

const originalText = new WeakMap<Node, string>()
const originalAttributes = new WeakMap<Element, Map<string, string>>()
const observerOptions: MutationObserverInit = { childList: true, subtree: true, characterData: true }
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
  const translated = locale.value === 'zh-CN'
    ? zhTextTranslations[trimmed]
    : enTextTranslations[trimmed]
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
    if (translated !== current) node.textContent = translated
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
