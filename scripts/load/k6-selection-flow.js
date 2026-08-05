import http from 'k6/http'
import { check, fail, sleep } from 'k6'
import exec from 'k6/execution'
import { Counter, Rate, Trend } from 'k6/metrics'
import { SharedArray } from 'k6/data'

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080'
const fixturesPath = __ENV.LOAD_FIXTURES || './scripts/load/generated/selection-fixtures.json'
const fixtures = new SharedArray('selection fixtures', () => JSON.parse(open(fixturesPath)).students)
const teamFixtures = new SharedArray('team fixtures', () => JSON.parse(open(fixturesPath)).teams || [])
const target = JSON.parse(open(fixturesPath)).target || {}

const businessErrors = new Rate('selection_business_errors')
const mainQuery = new Trend('selection_main_query_duration', true)
const confirmDuration = new Trend('selection_confirm_duration', true)
const validConfirmations = new Counter('selection_valid_confirmations')
const leaseAcquired = new Counter('selection_lease_acquired')
const duplicateConfirmationAccepted = new Counter('duplicate_confirmation_accepted')

export const options = {
  scenarios: {
    five_hundred_active_leases: {
      executor: 'per-vu-iterations', exec: 'activeLeaseFlow',
      vus: Number(__ENV.ENTRY_VUS || 500), iterations: 1,
      maxDuration: '3m', startTime: '0s',
    },
    one_hundred_compete_one_bed: {
      executor: 'per-vu-iterations', exec: 'sameBedCompetition',
      vus: Number(__ENV.BED_COMPETITION_VUS || 100), iterations: 1,
      maxDuration: '3m', startTime: '5s',
    },
    twenty_teams_compete_one_room: {
      executor: 'per-vu-iterations', exec: 'teamRoomCompetition',
      vus: Number(__ENV.TEAM_COMPETITION_VUS || 20), iterations: 1,
      maxDuration: '3m', startTime: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    selection_business_errors: ['rate<0.01'],
    selection_main_query_duration: ['p(95)<800'],
    selection_confirm_duration: ['p(95)<1500'],
    duplicate_confirmation_accepted: ['count==0'],
  },
}

function fixture(index = exec.vu.idInTest - 1) {
  const item = fixtures[index % fixtures.length]
  if (!item) fail(`missing fixture for VU ${index + 1}`)
  return item
}

function requestId() {
  return `k6-${exec.scenario.name}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}`
}

function login(student) {
  const response = http.post(`${baseUrl}/api/v1/auth/login`, JSON.stringify({
    loginName: student.loginName,
    password: student.password,
  }), { headers: { 'Content-Type': 'application/json' }, tags: { operation: 'login' } })
  check(response, { 'login succeeded': (r) => r.status === 200 }) || fail(`login failed: ${response.status}`)
  const body = response.json()
  const token = body?.data?.token
  if (!token) fail('login response did not contain token')
  return token
}

function headers(token, leaseToken = '') {
  const result = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
    'X-Request-Id': requestId(),
  }
  if (leaseToken) result['X-Selection-Lease-Token'] = leaseToken
  return result
}

function acquireLease(token) {
  const response = http.post(`${baseUrl}/api/v1/student/selection-leases`, null, {
    headers: headers(token), tags: { operation: 'lease_acquire' },
  })
  check(response, { 'lease acquired or disabled': (r) => r.status === 200 }) || fail(`lease failed: ${response.status}`)
  const data = response.json()?.data || {}
  if (data.limited && data.token) leaseAcquired.add(1)
  return data.token || ''
}

function releaseLease(token, leaseToken) {
  if (!leaseToken) return
  http.del(`${baseUrl}/api/v1/student/selection-leases/${encodeURIComponent(leaseToken)}`, null, {
    headers: headers(token, leaseToken), tags: { operation: 'lease_release' },
  })
}

export function activeLeaseFlow() {
  const student = fixture()
  const token = login(student)
  const leaseToken = acquireLease(token)
  const started = Date.now()
  const rooms = http.get(`${baseUrl}/api/v1/student/batches/${student.batchId}/rooms`, {
    headers: headers(token, leaseToken), tags: { operation: 'room_query' },
  })
  mainQuery.add(Date.now() - started)
  businessErrors.add(rooms.status >= 500)
  check(rooms, { 'candidate rooms loaded': (r) => r.status === 200 })

  sleep(Number(__ENV.LEASE_HOLD_SECONDS || 35))
  if (leaseToken) {
    const renew = http.put(`${baseUrl}/api/v1/student/selection-leases/${encodeURIComponent(leaseToken)}`, null, {
      headers: headers(token, leaseToken), tags: { operation: 'lease_renew' },
    })
    check(renew, { 'lease renewed': (r) => r.status === 200 })
  }
  releaseLease(token, leaseToken)
}

export function sameBedCompetition() {
  const student = fixture()
  const batchId = Number(target.batchId || student.batchId)
  const bedId = Number(target.bedId)
  if (!bedId) fail('target.bedId is required')
  const token = login(student)
  const leaseToken = acquireLease(token)
  const hold = http.post(`${baseUrl}/api/v1/student/batches/${batchId}/beds/${bedId}/hold`, null, {
    headers: headers(token, leaseToken), tags: { operation: 'same_bed_hold' },
  })
  if (hold.status === 200) {
    const holdToken = hold.json()?.data?.token
    const started = Date.now()
    const confirm = http.post(
      `${baseUrl}/api/v1/student/batches/${batchId}/beds/${bedId}/confirm`,
      JSON.stringify({ token: holdToken }),
      { headers: headers(token, leaseToken), tags: { operation: 'same_bed_confirm' } },
    )
    confirmDuration.add(Date.now() - started)
    if (confirm.status === 200) validConfirmations.add(1)
    const repeated = http.post(
      `${baseUrl}/api/v1/student/batches/${batchId}/beds/${bedId}/confirm`,
      JSON.stringify({ token: holdToken }),
      { headers: headers(token, leaseToken), tags: { operation: 'duplicate_confirm' } },
    )
    if (repeated.status === 200) duplicateConfirmationAccepted.add(1)
    check(repeated, { 'duplicate confirmation rejected': (r) => r.status === 409 || r.status === 400 })
  } else {
    check(hold, { 'competing hold rejected safely': (r) => [409, 423, 429].includes(r.status) })
  }
  releaseLease(token, leaseToken)
}

export function teamRoomCompetition() {
  if (teamFixtures.length === 0) return
  const team = teamFixtures[(exec.vu.idInTest - 1) % teamFixtures.length]
  const token = login(team.leader)
  const leaseToken = acquireLease(token)
  const roomId = Number(target.roomId)
  if (!roomId) fail('target.roomId is required for team competition')
  const response = http.post(
    `${baseUrl}/api/v1/student/batches/${team.batchId}/teams/${team.teamId}/rooms/${roomId}/select`,
    null,
    { headers: headers(token, leaseToken), tags: { operation: 'team_room_select' } },
  )
  check(response, {
    'team room selection is atomic': (r) => r.status === 200 || [409, 423, 429].includes(r.status),
  })
  businessErrors.add(response.status >= 500)
  releaseLease(token, leaseToken)
}

export function handleSummary(data) {
  return {
    [__ENV.K6_SUMMARY || 'scripts/load/generated/k6-summary.json']: JSON.stringify(data, null, 2),
    stdout: JSON.stringify({
      validConfirmations: data.metrics.selection_valid_confirmations?.values?.count || 0,
      duplicateAccepted: data.metrics.duplicate_confirmation_accepted?.values?.count || 0,
      p95MainQuery: data.metrics.selection_main_query_duration?.values?.['p(95)'] || 0,
      p95Confirm: data.metrics.selection_confirm_duration?.values?.['p(95)'] || 0,
    }, null, 2),
  }
}
