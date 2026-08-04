<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  maxlength?: number
  tokenExamples?: Record<string, string>
}>(), {
  placeholder: '',
  maxlength: 1000,
  tokenExamples: () => ({}),
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  focus: []
}>()

const editor = ref<HTMLElement | null>(null)
let internalUpdate = false

watch(() => props.modelValue, (value) => {
  if (!internalUpdate && document.activeElement !== editor.value) render(value)
})

onMounted(() => render(props.modelValue))

function escapeHtml(value: string) {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
}

function render(value: string) {
  if (!editor.value) return
  const pattern = /\{\{([^{}]+)}}/g
  let cursor = 0
  let html = ''
  for (const match of value.matchAll(pattern)) {
    const index = match.index ?? 0
    html += escapeHtml(value.slice(cursor, index)).replaceAll('\n', '<br>')
    const name = match[1]
    const token = match[0]
    const example = props.tokenExamples[name] ?? `登录后显示对应的${name}`
    html += `<span class="welcome-token" contenteditable="false" data-token="${escapeHtml(token)}" title="示例：${escapeHtml(example)}">${escapeHtml(name)}</span>`
    cursor = index + token.length
  }
  html += escapeHtml(value.slice(cursor)).replaceAll('\n', '<br>')
  editor.value.innerHTML = html
}

function serialize(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent ?? ''
  if (node instanceof HTMLElement && node.classList.contains('welcome-token')) {
    return node.dataset.token ?? ''
  }
  if (node instanceof HTMLBRElement) return '\n'
  return Array.from(node.childNodes).map(serialize).join('')
}

function sync() {
  if (!editor.value) return
  const value = serialize(editor.value).replace(/\n{3,}/g, '\n\n')
  if (value.length > props.maxlength) {
    render(props.modelValue)
    return
  }
  internalUpdate = true
  emit('update:modelValue', value)
  void nextTick(() => { internalUpdate = false })
}

function insertToken(name: string) {
  const root = editor.value
  if (!root) return
  root.focus()
  const token = `{{${name}}}`
  const chip = document.createElement('span')
  chip.className = 'welcome-token'
  chip.contentEditable = 'false'
  chip.dataset.token = token
  chip.textContent = name
  chip.title = `示例：${props.tokenExamples[name] ?? `登录后显示对应的${name}`}`
  const spacer = document.createTextNode(' ')
  const selection = window.getSelection()
  const range = selection?.rangeCount ? selection.getRangeAt(0) : null
  if (range && root.contains(range.commonAncestorContainer)) {
    range.deleteContents()
    range.insertNode(spacer)
    range.insertNode(chip)
    range.setStartAfter(spacer)
    range.collapse(true)
    selection?.removeAllRanges()
    selection?.addRange(range)
  } else {
    root.append(chip, spacer)
  }
  sync()
}

defineExpose({ insertToken, focus: () => editor.value?.focus() })
</script>

<template>
  <div
    ref="editor"
    class="welcome-message-editor input"
    contenteditable="true"
    role="textbox"
    aria-multiline="true"
    :data-placeholder="placeholder"
    @focus="emit('focus')"
    @input="sync"
    @blur="sync"
    @paste.prevent="(event: ClipboardEvent) => document.execCommand('insertText', false, event.clipboardData?.getData('text/plain') ?? '')"
  />
</template>

<style scoped>
.welcome-message-editor{min-height:142px;height:100%;padding:12px 13px;overflow:auto;white-space:pre-wrap;line-height:1.75;cursor:text}.welcome-message-editor:empty::before{content:attr(data-placeholder);color:var(--muted);pointer-events:none}.welcome-message-editor:focus{outline:none}.welcome-message-editor :deep(.welcome-token){display:inline-flex;align-items:center;margin:1px 3px;padding:2px 9px;border:1px solid #bed5ff;border-radius:999px;color:#245da8;background:#eef5ff;font-size:12px;font-weight:700;line-height:1.7;vertical-align:baseline;cursor:help;user-select:all}
</style>
