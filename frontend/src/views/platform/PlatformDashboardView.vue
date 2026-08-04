<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { platformApi, type FeatureEntitlement } from '../../platform/api'

const subscription = ref<Record<string, unknown>>({})
const quotas = ref<Record<string, unknown>>({})
const features = ref<FeatureEntitlement[]>([])
const error = ref('')
const message = ref('')
const loading = ref(true)
const responseTimeMs = ref(0)
const updatedAt = ref('')
const online = ref(window.navigator.onLine)
const showRedisClear = ref(false)
const redisReason = ref('')
const redisConfirmation = ref('')
const clearingRedis = ref(false)

const usage = computed(() => Array.isArray(quotas.value.usage) ? quotas.value.usage as Record<string, unknown>[] : [])
const enabledFeatureCount = computed(() => features.value.filter((item) => item.effectiveEnabled).length)
const warningCount = computed(() => usage.value.filter((item) => Number(item.ratio ?? 0) >= .8).length)
const averageUsage = computed(() => {
  if (!usage.value.length) return 0
  return Math.round(usage.value.reduce((sum, item) => sum + Number(item.ratio ?? 0), 0) / usage.value.length * 100)
})
const serviceHealthy = computed(() => String(subscription.value.serviceStatus ?? '') === 'ACTIVE' && !Boolean(subscription.value.emergencyStop))
const canClearRedis = computed(() => redisConfirmation.value === 'CLEAR_REDIS' && Boolean(redisReason.value.trim()))

onMounted(() => {
  window.addEventListener('online', updateOnlineState)
  window.addEventListener('offline', updateOnlineState)
  void load()
})

function updateOnlineState() { online.value = window.navigator.onLine }

async function load() {
  loading.value = true; error.value = ''
  const started = performance.now()
  try {
    ;[subscription.value, quotas.value, features.value] = await Promise.all([
      platformApi.subscription(), platformApi.quotas(), platformApi.featureEntitlements(false),
    ])
    responseTimeMs.value = Math.round(performance.now() - started)
    updatedAt.value = new Date().toLocaleString()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '加载失败' }
  finally { loading.value = false }
}

function openRedisClear() {
  redisReason.value = ''
  redisConfirmation.value = ''
  message.value = ''
  error.value = ''
  showRedisClear.value = true
}

async function clearRedis() {
  if (!canClearRedis.value || clearingRedis.value) return
  clearingRedis.value = true
  try {
    const result = await platformApi.clearRedis(redisReason.value.trim(), 'CLEAR_REDIS')
    message.value = `当前Redis数据库已清空，清理前共有 ${result.beforeKeyCount} 个键。其他Redis数据库未受影响。`
    showRedisClear.value = false
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '清空Redis失败'
  } finally {
    clearingRedis.value = false
  }
}

function serviceStatus(value: unknown) {
  return ({ ACTIVE: '运行正常', SUSPENDED: '服务已暂停', TERMINATED: '服务已终止' } as Record<string, string>)[String(value)] ?? '状态未知'
}
function quotaName(code: unknown) {
  return ({ MAX_STUDENTS: '学生容量', MAX_ROOMS: '宿舍房间', MAX_BEDS: '床位容量', MAX_ACTIVE_BATCHES: '同时开放批次', MAX_ADMIN_USERS: '管理员账号' } as Record<string, string>)[String(code)] ?? '资源项目'
}
</script>

<template>
  <section class="overview-page">
    <header class="page-heading"><div><p class="eyebrow">服务运行情况</p><h1>服务概览</h1><p>面向当前学校的服务状态、功能授权、资源使用和访问性能。</p></div><button type="button" :disabled="loading" @click="load">{{ loading ? '正在刷新…' : '刷新状态' }}</button></header>
    <p v-if="error" class="error-message">{{ error }}</p>
    <p v-if="message" class="success-message">{{ message }}</p>

    <div class="summary-grid">
      <article><span>服务状态</span><strong :class="serviceHealthy ? 'healthy' : 'warning'">{{ serviceStatus(subscription.serviceStatus) }}</strong><small>{{ subscription.emergencyStop ? '已启用紧急停止' : '业务接口可正常访问' }}</small></article>
      <article><span>当前套餐</span><strong>{{ subscription.planName || '未配置' }}</strong><small>{{ subscription.revisionName || '暂无修订信息' }}</small></article>
      <article><span>已启用功能</span><strong>{{ enabledFeatureCount }}</strong><small>按当前授权实时计算</small></article>
      <article><span>平均资源使用率</span><strong>{{ averageUsage }}%</strong><small>{{ warningCount ? `${warningCount}项需要关注` : '全部处于安全范围' }}</small></article>
    </div>

    <div class="dashboard-grid">
      <section class="panel wide">
        <div class="section-title"><div><h2>资源使用情况</h2><p>达到80%时提醒，达到100%后限制继续新增。</p></div><RouterLink to="/platform/quotas">管理配额</RouterLink></div>
        <div v-if="usage.length" class="usage-list">
          <article v-for="item in usage" :key="String(item.quotaCode)"><div><strong>{{ quotaName(item.quotaCode) }}</strong><span>{{ item.used }} / {{ item.limit }}</span></div><div class="progress"><i :style="{ width: `${Math.min(100, Math.round(Number(item.ratio || 0) * 100))}%` }" /></div><small>{{ Math.round(Number(item.ratio || 0) * 100) }}% · {{ Number(item.ratio || 0) >= 1 ? '已达上限' : Number(item.ratio || 0) >= .8 ? '接近上限' : '使用正常' }}</small></article>
        </div>
        <p v-else class="empty">暂无资源配额数据。</p>
      </section>

      <section class="panel monitor-card">
        <div class="section-title"><div><h2>访问性能</h2><p>从当前管理端发起请求的实际响应情况。</p></div></div>
        <dl><div><dt>本次加载耗时</dt><dd>{{ responseTimeMs }}毫秒</dd></div><div><dt>网络状态</dt><dd>{{ online ? '已连接' : '已断开' }}</dd></div><div><dt>配额预警</dt><dd>{{ warningCount }}项</dd></div><div><dt>最后更新</dt><dd>{{ updatedAt || '-' }}</dd></div></dl>
      </section>

      <section class="panel quick-actions">
        <div class="section-title"><div><h2>常用操作</h2><p>集中进入日常服务维护功能。</p></div></div>
        <div><RouterLink to="/platform/features">调整功能授权</RouterLink><RouterLink to="/platform/subscription">变更服务套餐</RouterLink><RouterLink to="/platform/audit">查看操作审计</RouterLink></div>
      </section>

      <section class="panel danger-zone">
        <div class="section-title"><div><h2>高风险维护</h2><p>只清空当前应用选择的Redis数据库，不会执行全实例清空。</p></div></div>
        <div class="danger-description"><strong>清空Redis</strong><span>将退出登录令牌、临时床位占用和可重建缓存全部清除。MySQL正式业务数据不受影响。</span></div>
        <button class="danger-button" type="button" @click="openRedisClear">清空Redis</button>
      </section>
    </div>

    <div v-if="showRedisClear" class="danger-overlay" @click.self="showRedisClear=false">
      <section class="danger-dialog" role="dialog" aria-modal="true" aria-labelledby="redis-clear-title">
        <div><p class="eyebrow">高风险操作</p><h2 id="redis-clear-title">确认清空当前Redis数据库</h2><p>此操作会使当前登录令牌和所有临时占用立即失效。不会清空其他Redis数据库，也不会删除MySQL数据。</p></div>
        <label><span>操作原因</span><textarea v-model="redisReason" rows="3" maxlength="500" placeholder="说明为什么需要清空Redis" /></label>
        <label><span>输入 CLEAR_REDIS 确认</span><input v-model.trim="redisConfirmation" autocomplete="off" placeholder="CLEAR_REDIS" /></label>
        <div class="dialog-actions"><button type="button" @click="showRedisClear=false">取消</button><button class="danger-button" type="button" :disabled="!canClearRedis || clearingRedis" @click="clearRedis">{{ clearingRedis ? '正在清空…' : '确认清空当前数据库' }}</button></div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.overview-page{display:grid;gap:22px}.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.page-heading h1{margin:0 0 8px}.page-heading p{margin:0;color:#69758b}.page-heading button,.section-title a,.quick-actions a,.dialog-actions button{padding:10px 14px;border:1px solid #d7dfeb;border-radius:10px;color:#315c9e;background:#fff;cursor:pointer;text-decoration:none}.summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.summary-grid article,.panel{padding:20px;border:1px solid #dde4ef;border-radius:18px;background:#fff;box-shadow:0 12px 28px rgba(22,43,82,.06)}.summary-grid span,.summary-grid small{display:block;color:#69758b;font-size:.76rem}.summary-grid strong{display:block;margin:12px 0 8px;font-size:1.65rem}.healthy{color:#158467}.warning{color:#b66a16}.dashboard-grid{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(280px,.8fr);gap:18px}.wide{grid-row:span 3}.section-title{display:flex;justify-content:space-between;align-items:flex-start;gap:15px;margin-bottom:18px}.section-title h2{margin:0 0 5px;font-size:1.05rem}.section-title p{margin:0;color:#69758b;font-size:.78rem}.usage-list{display:grid;gap:16px}.usage-list article>div:first-child{display:flex;justify-content:space-between;gap:12px}.usage-list span,.usage-list small{color:#69758b;font-size:.76rem}.progress{height:9px;margin:9px 0 6px;overflow:hidden;border-radius:999px;background:#edf1f7}.progress i{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#3b82f6,#7c5ce1)}dl{display:grid;gap:12px;margin:0}dl div{display:flex;justify-content:space-between;gap:10px;padding:11px 0;border-bottom:1px solid #edf1f7}dt{color:#69758b;font-size:.76rem}dd{margin:0;font-weight:700;font-size:.8rem;text-align:right}.quick-actions>div:last-child{display:grid;gap:10px}.error-message,.success-message{padding:12px;border-radius:10px}.error-message{color:#9b2838;background:#fff0f2}.success-message{color:#16684f;background:#edf9f4}.empty{color:#69758b;text-align:center}.eyebrow{margin:0 0 7px;color:#64789c;font-size:.68rem;font-weight:700;letter-spacing:.14em}.danger-zone{border-color:#efc0c6;background:#fffafa}.danger-description{display:grid;gap:6px;margin-bottom:14px}.danger-description span{color:#7b5960;font-size:.8rem;line-height:1.5}.danger-button{padding:11px 15px;border:0;border-radius:10px;color:#fff;background:#b93246;cursor:pointer}.danger-button:disabled{opacity:.48;cursor:not-allowed}.danger-overlay{position:fixed;z-index:1800;inset:0;display:grid;place-items:center;padding:18px;background:rgba(25,12,18,.75);backdrop-filter:blur(8px)}.danger-dialog{display:grid;gap:16px;width:min(580px,100%);padding:26px;border-radius:24px;background:#fff;box-shadow:0 28px 80px rgba(37,10,18,.34)}.danger-dialog h2{margin:0 0 8px}.danger-dialog p{margin:0;color:#6d5a60;line-height:1.6}.danger-dialog label{display:grid;gap:7px}.danger-dialog textarea,.danger-dialog input{padding:12px;border:1px solid #d8c7cb;border-radius:12px;font:inherit}.dialog-actions{display:flex;justify-content:flex-end;gap:10px}@media(max-width:1000px){.summary-grid{grid-template-columns:repeat(2,1fr)}.dashboard-grid{grid-template-columns:1fr}.wide{grid-row:auto}}@media(max-width:620px){.summary-grid{grid-template-columns:1fr}.page-heading{display:grid}.danger-overlay{align-items:end;padding:0}.danger-dialog{padding:20px;border-radius:24px 24px 0 0}.dialog-actions{display:grid;grid-template-columns:1fr}}
</style>
