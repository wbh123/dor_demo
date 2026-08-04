const timers = new WeakMap<HTMLElement, number>()
const messageSnapshots = new WeakMap<HTMLElement, string>()

function noticeText(element: HTMLElement) {
  return Array.from(element.childNodes)
    .filter((node) => !(node instanceof HTMLElement && node.classList.contains('notice-close')))
    .map((node) => node.textContent ?? '')
    .join('')
    .trim()
}

function close(element: HTMLElement) {
  const timer = timers.get(element)
  if (timer) window.clearTimeout(timer)
  timers.delete(element)
  element.classList.add('transient-success-hidden')
}

function enhance(element: HTMLElement) {
  const message = noticeText(element)
  if (!message) return
  const previous = messageSnapshots.get(element)
  if (previous === message && element.dataset.transientNoticeReady === 'true') return
  messageSnapshots.set(element, message)
  element.dataset.transientNoticeReady = 'true'
  element.classList.add('transient-success-notice')
  element.classList.remove('transient-success-hidden')

  let button = element.querySelector<HTMLButtonElement>('.notice-close')
  if (!button) {
    button = document.createElement('button')
    button.type = 'button'
    button.className = 'notice-close'
    button.setAttribute('aria-label', '关闭提示')
    button.textContent = '×'
    button.addEventListener('click', () => close(element))
    element.append(button)
  }

  const previousTimer = timers.get(element)
  if (previousTimer) window.clearTimeout(previousTimer)
  timers.set(element, window.setTimeout(() => close(element), 3000))
}

function scan(root: ParentNode = document) {
  root.querySelectorAll<HTMLElement>('.alert.success').forEach(enhance)
}

export function installTransientSuccessNotices() {
  const start = () => {
    scan()
    const observer = new MutationObserver((records) => {
      for (const record of records) {
        if (record.type === 'characterData') {
          const parent = record.target.parentElement
          if (parent?.matches('.alert.success')) enhance(parent)
          continue
        }
        record.addedNodes.forEach((node) => {
          if (!(node instanceof HTMLElement)) return
          if (node.matches('.alert.success')) enhance(node)
          scan(node)
        })
      }
    })
    observer.observe(document.body, { childList: true, subtree: true, characterData: true })
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, { once: true })
  else start()
}
