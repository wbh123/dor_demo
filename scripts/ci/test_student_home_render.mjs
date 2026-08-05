#!/usr/bin/env node
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(scriptDir, '../..')
const frontendRoot = path.join(root, 'frontend')
const frontendRequire = createRequire(path.join(frontendRoot, 'package.json'))

const importFrontendPackage = async (name) => {
  const resolved = frontendRequire.resolve(name)
  return import(pathToFileURL(resolved).href)
}

const storage = new Map()
Object.defineProperty(globalThis, 'navigator', {
  value: { language: 'zh-CN' },
  configurable: true,
})
Object.defineProperty(globalThis, 'localStorage', {
  value: {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear(),
  },
  configurable: true,
})
Object.defineProperty(globalThis, 'document', {
  value: {
    documentElement: { lang: 'zh-CN' },
    body: null,
  },
  configurable: true,
})
globalThis.Node = class Node {}
globalThis.Element = class Element {}
globalThis.MutationObserver = class MutationObserver {
  observe() {}
  disconnect() {}
}

const { createServer } = await importFrontendPackage('vite')
const { createSSRApp, h } = await importFrontendPackage('vue')
const { renderToString } = await importFrontendPackage('@vue/server-renderer')

const server = await createServer({
  root: frontendRoot,
  appType: 'custom',
  logLevel: 'error',
  server: { middlewareMode: true },
})

try {
  const loaded = await server.ssrLoadModule('/src/views/student/StudentHomeContent.vue')
  const StudentHomeContent = loaded.default
  const warnings = []
  const app = createSSRApp({ render: () => h(StudentHomeContent) })
  app.component('RouterLink', {
    props: ['to'],
    setup(_props, { slots }) {
      return () => h('a', slots.default?.())
    },
  })
  app.config.warnHandler = (message) => warnings.push(String(message))

  const html = await renderToString(app)
  assert.match(html, /欢迎回来|WELCOME BACK/)
  assert.doesNotMatch(warnings.join('\n'), /subtitle.*function/i)
  assert.doesNotMatch(warnings.join('\n'), /Property .*subtitle.*was accessed during render/i)
  console.log('StudentHomeContent real SSR render passed')
} finally {
  await server.close()
}
