import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const router = await readFile(new URL('../src/router/index.ts', import.meta.url), 'utf8')
const page = await readFile(new URL('../src/views/admin/AdminSystemReadinessView.vue', import.meta.url), 'utf8')
const operations = await readFile(new URL('../src/views/admin/AdminOperationsView.vue', import.meta.url), 'utf8')

test('registers the admin system readiness page', () => {
  assert.match(router, /path:\s*'admin\/system-readiness'/)
  assert.match(router, /AdminSystemReadinessView\.vue/)
  assert.match(operations, /to="\/admin\/system-readiness"/)
})

test('loads readiness only on mount and manual recheck', () => {
  assert.match(page, /api\.get<ReadinessReport>\('\/api\/v1\/admin\/system-readiness'\)/)
  assert.match(page, /onMounted\(runCheck\)/)
  assert.match(page, /@click="runCheck"/)
  assert.doesNotMatch(page, /setInterval/)
  assert.doesNotMatch(page, /setTimeout\([^)]*runCheck/)
})

test('renders backend decisions and action routes without deriving overall status locally', () => {
  assert.match(page, /report\.overallStatus === 'READY'/)
  assert.match(page, /report\.overallStatus === 'READY_WITH_WARNINGS'/)
  assert.match(page, /report\.overallStatus === 'BLOCKED'/)
  assert.match(page, /v-if="item\.actionRoute"/)
  assert.match(page, /:to="item\.actionRoute"/)
  assert.match(page, /item\.suggestedAction/)
  assert.match(page, /item\.evidence/)
  assert.match(page, /item\.checkedAt/)
  assert.doesNotMatch(page, /overallStatus\s*=(?!=)/)
})
