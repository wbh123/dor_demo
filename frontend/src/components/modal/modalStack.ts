import { nextTick } from 'vue'

interface ModalEntry {
  id: string
  root: HTMLElement | null
  restoreFocus: HTMLElement | null
}

const stack: ModalEntry[] = []
let previousBodyOverflow = ''

export function registerModal(id: string, root: HTMLElement | null) {
  if (stack.some((entry) => entry.id === id)) return
  if (stack.length === 0) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  }
  stack.push({
    id,
    root,
    restoreFocus: document.activeElement instanceof HTMLElement ? document.activeElement : null,
  })
}

export function updateModalRoot(id: string, root: HTMLElement | null) {
  const entry = stack.find((item) => item.id === id)
  if (entry) entry.root = root
}

export async function unregisterModal(id: string) {
  const index = stack.findIndex((entry) => entry.id === id)
  if (index < 0) return
  const [entry] = stack.splice(index, 1)
  if (stack.length === 0) {
    document.body.style.overflow = previousBodyOverflow
  }
  await nextTick()
  entry.restoreFocus?.focus({ preventScroll: true })
}

export function isTopModal(id: string) {
  return stack.at(-1)?.id === id
}

export function modalDepth(id: string) {
  const index = stack.findIndex((entry) => entry.id === id)
  return index < 0 ? 0 : index + 1
}

export function focusableElements(root: HTMLElement | null) {
  if (!root) return []
  return [...root.querySelectorAll<HTMLElement>(
    'a[href],button:not([disabled]),textarea:not([disabled]),input:not([disabled]),select:not([disabled]),[tabindex]:not([tabindex="-1"])',
  )].filter((element) => !element.hasAttribute('hidden') && element.offsetParent !== null)
}
