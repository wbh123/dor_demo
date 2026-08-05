import { nextTick } from 'vue'

interface ModalEntry {
  id: string
  root: HTMLElement | null
  restoreFocus: HTMLElement | null
}

interface BackgroundState {
  inert: boolean
  ariaHidden: string | null
}

const stack: ModalEntry[] = []
const backgroundStates = new Map<HTMLElement, BackgroundState>()
let previousBodyOverflow = ''
let containingFocus = false

export function registerModal(id: string, root: HTMLElement | null) {
  if (stack.some((entry) => entry.id === id)) return
  if (stack.length === 0) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    document.addEventListener('focusin', containFocus, true)
  }
  stack.push({
    id,
    root,
    restoreFocus: document.activeElement instanceof HTMLElement ? document.activeElement : null,
  })
  applyBackgroundIsolation()
}

export function updateModalRoot(id: string, root: HTMLElement | null) {
  const entry = stack.find((item) => item.id === id)
  if (!entry) return
  entry.root = root
  applyBackgroundIsolation()
}

export async function unregisterModal(id: string) {
  const index = stack.findIndex((entry) => entry.id === id)
  if (index < 0) return
  const [entry] = stack.splice(index, 1)
  if (stack.length === 0) {
    document.body.style.overflow = previousBodyOverflow
    document.removeEventListener('focusin', containFocus, true)
    restoreBackgroundIsolation()
  } else {
    applyBackgroundIsolation()
  }
  await nextTick()
  if (entry.restoreFocus?.isConnected && !entry.restoreFocus.closest('[inert]')) {
    entry.restoreFocus.focus({ preventScroll: true })
  } else {
    focusTopModal()
  }
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
    'a[href],area[href],button:not([disabled]),input:not([disabled]):not([type="hidden"]),select:not([disabled]),textarea:not([disabled]),iframe,object,embed,[contenteditable="true"],[tabindex]:not([tabindex="-1"])',
  )].filter((element) =>
    !element.hasAttribute('hidden')
    && element.getAttribute('aria-hidden') !== 'true'
    && !element.closest('[inert]')
    && element.getClientRects().length > 0,
  )
}

function containFocus(event: FocusEvent) {
  if (containingFocus) return
  const top = stack.at(-1)
  if (!top?.root) return
  const target = event.target
  if (target instanceof Node && top.root.contains(target)) return
  containingFocus = true
  focusTopModal()
  queueMicrotask(() => { containingFocus = false })
}

function focusTopModal() {
  const root = stack.at(-1)?.root
  if (!root) return
  const preferred = root.querySelector<HTMLElement>('[autofocus],[data-modal-autofocus]')
  const target = preferred ?? focusableElements(root)[0] ?? root.querySelector<HTMLElement>('[role="dialog"], [role="alertdialog"]') ?? root
  target.focus({ preventScroll: true })
}

function applyBackgroundIsolation() {
  restoreBackgroundIsolation()
  const topRoot = stack.at(-1)?.root
  if (!topRoot) return
  for (const child of [...document.body.children]) {
    if (!(child instanceof HTMLElement) || child === topRoot || child.contains(topRoot)) continue
    backgroundStates.set(child, {
      inert: child.inert,
      ariaHidden: child.getAttribute('aria-hidden'),
    })
    child.inert = true
    child.setAttribute('aria-hidden', 'true')
  }
}

function restoreBackgroundIsolation() {
  for (const [element, state] of backgroundStates) {
    if (!element.isConnected) continue
    element.inert = state.inert
    if (state.ariaHidden == null) element.removeAttribute('aria-hidden')
    else element.setAttribute('aria-hidden', state.ariaHidden)
  }
  backgroundStates.clear()
}
