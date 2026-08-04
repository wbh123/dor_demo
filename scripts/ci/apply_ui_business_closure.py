#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def path(name: str) -> Path:
    return ROOT / name


def read(name: str) -> str:
    return path(name).read_text(encoding="utf-8")


def write(name: str, content: str) -> None:
    path(name).parent.mkdir(parents=True, exist_ok=True)
    path(name).write_text(content, encoding="utf-8")


def replace_once(name: str, old: str, new: str) -> None:
    content = read(name)
    if old not in content:
        raise RuntimeError(f"source fragment not found in {name}: {old[:120]!r}")
    write(name, content.replace(old, new, 1))


# Environment-driven school brand and student support phone.
replace_once(
    ".env.example",
    "VITE_CAMPUS_NAME=示例校区\nVITE_OPERATOR_NAME=运营单位信息待填写",
    "VITE_CAMPUS_NAME=示例校区\nVITE_APP_TITLE=示例大学选寝\nVITE_APP_SUBTITLE=宿舍智能选择系统\nVITE_ADMIN_CONTACT_PHONE=000-0000-0000\nVITE_OPERATOR_NAME=运营单位信息待填写",
)

# Fix the reversed bilingual subtitle selector.
replace_once(
    "frontend/src/i18n/index.ts",
    "return locale.value === 'zh-CN' ? english : chinese",
    "return locale.value === 'zh-CN' ? chinese : english",
)

# Keep one canonical preference response: answers is a question-code object.
replace_once(
    "frontend/src/views/student/StudentHomeContent.vue",
    "const questions = computed(() => (questionnaire.value.questions ?? []) as DataObject[])\nconst savedAnswers = computed(() => (questionnaire.value.answers ?? []) as DataObject[])",
    "const questions = computed(() => (questionnaire.value.questions ?? []) as DataObject[])\nconst profileAnswers = computed(() => (questionnaire.value.answers ?? {}) as Record<string, unknown>)\nconst profileAnswerEntries = computed(() => questions.value.flatMap((question) => {\n  const code = String(question.question_code)\n  if (!(code in profileAnswers.value)) return []\n  return [{ question_id: question.id, answer_json: profileAnswers.value[code] } as DataObject]\n}))",
)
replace_once(
    "frontend/src/views/student/StudentHomeContent.vue",
    "return savedAnswers.value\n    .map((answer) => {",
    "return profileAnswerEntries.value\n    .map((answer) => {",
)

# Put preference and phone actions into the page flow and expose the support phone.
replace_once(
    "frontend/src/views/student/StudentHomeView.vue",
    "const { translateError } = useI18n()",
    "const { translateError } = useI18n()\nconst adminContactPhone = String(import.meta.env.VITE_ADMIN_CONTACT_PHONE || '000-0000-0000')",
)
replace_once(
    "frontend/src/views/student/StudentHomeView.vue",
    "    <p class=\"cross-batch-preference-note\">个人偏好可跨批次复用，即使当前没有开放批次，也可在管理员开放后提前维护。</p>\n    <StudentHomeContent :key=\"contentKey\" />\n    <button class=\"phone-edit-fab\" type=\"button\" @click=\"openPhoneEditor\">修改手机号码</button>",
    "    <div class=\"student-home-actions\">\n      <p class=\"cross-batch-preference-note\">个人偏好可跨批次复用，即使当前没有开放批次，也可提前维护。<RouterLink to=\"/student/preferences\">进入个人偏好设置</RouterLink></p>\n      <div class=\"student-contact-strip\"><span>有疑问请致电管理员：<a :href=\"`tel:${adminContactPhone}`\">{{ adminContactPhone }}</a></span><button class=\"button ghost small\" type=\"button\" @click=\"openPhoneEditor\">修改手机号码</button></div>\n    </div>\n    <StudentHomeContent :key=\"contentKey\" />",
)
replace_once(
    "frontend/src/views/student/StudentHomeView.vue",
    ".student-home-wrapper{position:relative}.cross-batch-preference-note{margin:0 0 10px;padding:9px 12px;border-radius:12px;color:var(--muted);background:var(--soft);font-size:13px}.student-home-wrapper :deep(.light-text-button),.student-home-wrapper :deep(.phone-editor-dialog){display:none!important}.phone-edit-fab{position:fixed;right:24px;bottom:24px;z-index:90;padding:12px 18px;border:0;border-radius:999px;color:#fff;background:var(--primary);box-shadow:0 12px 30px rgba(30,78,140,.28);cursor:pointer}",
    ".student-home-wrapper{position:relative}.student-home-actions{display:grid;gap:10px;margin-bottom:10px}.cross-batch-preference-note{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:0;padding:9px 12px;border-radius:12px;color:var(--muted);background:var(--soft);font-size:13px}.cross-batch-preference-note a{color:var(--primary);font-weight:700;text-decoration:none}.student-contact-strip{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:9px 12px;border:1px solid var(--line);border-radius:12px;background:var(--panel,#fff);font-size:13px}.student-contact-strip a{color:var(--primary);font-weight:700}.student-home-wrapper :deep(.light-text-button),.student-home-wrapper :deep(.phone-editor-dialog){display:none!important}",
)
replace_once(
    "frontend/src/views/student/StudentHomeView.vue",
    "@media(max-width:640px){.phone-edit-fab{right:14px;bottom:14px}.phone-modal-overlay",
    "@media(max-width:640px){.cross-batch-preference-note,.student-contact-strip{align-items:flex-start;flex-direction:column}.phone-modal-overlay",
)

# Restore the student-information token bar in all welcome editors.
replace_once(
    "frontend/src/components/admin/WelcomeMessageEditor.vue",
    "import { nextTick, onMounted, ref, watch } from 'vue'",
    "import { computed, nextTick, onMounted, ref, watch } from 'vue'",
)
replace_once(
    "frontend/src/components/admin/WelcomeMessageEditor.vue",
    "const editor = ref<HTMLElement | null>(null)\nlet internalUpdate = false",
    "const editor = ref<HTMLElement | null>(null)\nconst tokenNames = computed(() => Object.keys(props.tokenExamples))\nlet internalUpdate = false",
)
replace_once(
    "frontend/src/components/admin/WelcomeMessageEditor.vue",
    "<template>\n  <div\n    ref=\"editor\"\n    class=\"welcome-message-editor input\"\n    contenteditable=\"true\"\n    role=\"textbox\"\n    aria-multiline=\"true\"\n    :data-placeholder=\"placeholder\"\n    @focus=\"emit('focus')\"\n    @input=\"sync\"\n    @blur=\"sync\"\n    @paste.prevent=\"pastePlainText\"\n  />\n</template>",
    "<template>\n  <div class=\"welcome-editor-shell\">\n    <div class=\"token-toolbar\"><span>插入学生信息</span><button v-for=\"name in tokenNames\" :key=\"name\" type=\"button\" :title=\"tokenExamples[name]\" @click=\"insertToken(name)\">{{ name }}</button></div>\n    <div\n      ref=\"editor\"\n      class=\"welcome-message-editor input\"\n      contenteditable=\"true\"\n      role=\"textbox\"\n      aria-multiline=\"true\"\n      :data-placeholder=\"placeholder\"\n      @focus=\"emit('focus')\"\n      @input=\"sync\"\n      @blur=\"sync\"\n      @paste.prevent=\"pastePlainText\"\n    />\n  </div>\n</template>",
)
replace_once(
    "frontend/src/components/admin/WelcomeMessageEditor.vue",
    ".welcome-message-editor{min-height:142px;height:100%;padding:12px 13px;overflow:auto;white-space:pre-wrap;line-height:1.75;cursor:text}",
    ".welcome-editor-shell{display:grid;gap:8px}.token-toolbar{display:flex;align-items:center;gap:7px;flex-wrap:wrap;padding:8px 10px;border:1px solid var(--line);border-radius:12px;background:var(--panel,#fff)}.token-toolbar>span{margin-right:3px;color:var(--muted);font-size:12px;font-weight:700}.token-toolbar button{padding:4px 9px;border:1px solid #bed5ff;border-radius:999px;color:#245da8;background:#eef5ff;font-size:12px;cursor:pointer}.welcome-message-editor{min-height:142px;height:100%;padding:12px 13px;overflow:auto;white-space:pre-wrap;line-height:1.75;cursor:text}",
)

# Welcome messages are language-based, while country codes still map to English.
for old, new in (
    ("请直接编辑美国卡片中的英文欢迎语", "请直接编辑英语卡片中的欢迎语"),
    ("中国和美国两个基础欢迎语均为必填项", "汉语和英语两个基础欢迎语均为必填项"),
    ("基础卡片按国家名称展示。其他国家共用一个编辑器；未配置时自动使用美国卡片中的英文欢迎语。", "基础卡片按语言展示。其他国家或地区可单独配置；未配置时自动使用英语欢迎语。"),
    ("<strong>中国</strong><small>中文基础欢迎语</small>", "<strong>汉语</strong><small>汉语基础欢迎语</small>"),
    ("填写面向中国学生的中文欢迎语", "填写默认汉语欢迎语"),
    ("<strong>美国</strong><small>英文基础欢迎语，也是其他国家的默认回退</small>", "<strong>英语</strong><small>英语基础欢迎语，也是其他国家或地区的默认回退</small>"),
    ("根据中国欢迎语自动翻译", "根据汉语欢迎语自动翻译"),
):
    replace_once("frontend/src/views/admin/AdminDashboardView.vue", old, new)
replace_once(
    "frontend/src/components/admin/CountryWelcomeEditor.vue",
    "未配置国家自动使用美国卡片中的英文欢迎语。",
    "未配置国家或地区自动使用英语欢迎语。",
)

# School application shell: configurable brand, merged residency menu and fixed account actions.
replace_once(
    "frontend/src/layouts/AppShell.vue",
    "const productName = `${institutionName}选寝`",
    "const productName = String(import.meta.env.VITE_APP_TITLE || `${institutionName}选寝`)\nconst productSubtitle = String(import.meta.env.VITE_APP_SUBTITLE || '宿舍智能选择系统')",
)
replace_once(
    "frontend/src/layouts/AppShell.vue",
    "  {to:'/admin/residencies',label:'在住管理',icon:icons.assignment},\n  {to:'/admin/bed-confirmations',label:'实际床位核查',icon:icons.bedCheck},",
    "  {to:'/admin/residencies',label:'在住与床位核查',icon:icons.assignment},",
)
replace_once(
    "frontend/src/layouts/AppShell.vue",
    "          <small>宿舍智能选择系统</small>",
    "          <small>{{ productSubtitle }}</small>",
)
replace_once(
    "frontend/src/layouts/AppShell.vue",
    "        <div class=\"user-card account-card-without-avatar\"><div><strong>{{ auth.user?.displayName }}</strong><small>{{ auth.isAdmin ? '业务管理员' : auth.user?.username }}</small></div></div>\n        <button class=\"button ghost full\" @click=\"logout\">退出登录</button>",
    "        <div class=\"user-card account-card-without-avatar\"><div><strong>{{ auth.user?.displayName }}</strong><small>{{ auth.isAdmin ? '业务管理员' : auth.user?.username }}</small></div></div>\n        <RouterLink v-if=\"auth.isAdmin\" to=\"/admin/profile/password\" class=\"button ghost full sidebar-action-link\">修改密码</RouterLink>\n        <button class=\"button ghost full\" @click=\"logout\">退出登录</button>",
)
replace_once(
    "frontend/src/layouts/AppShell.vue",
    "  return welcomeMessage(welcome?.messages) || String(welcome?.message ?? '')",
    "  return welcomeMessage(welcome?.messages)",
)
replace_once(
    "frontend/src/layouts/AppShell.vue",
    ".fixed-navigation-shell{display:block;min-height:100vh}.fixed-sidebar{position:fixed;inset:0 auto 0 0;width:260px;height:100vh;overflow-y:auto;z-index:30}",
    ".fixed-navigation-shell{display:block;min-height:100vh}.fixed-sidebar{position:fixed;inset:0 auto 0 0;display:flex;flex-direction:column;width:260px;height:100vh;overflow:hidden;z-index:30}.fixed-sidebar .nav-list{min-height:0;overflow-y:auto}.fixed-sidebar .sidebar-foot{flex:0 0 auto}.sidebar-action-link{text-decoration:none;text-align:center}",
)
replace_once(
    "frontend/src/layouts/AppShell.vue",
    ".school-brand{position:relative;isolation:isolate;display:flex;align-items:center;gap:11px;min-height:62px;padding:9px 12px}",
    ".school-brand{position:relative;z-index:5;isolation:isolate;display:flex;align-items:center;gap:11px;min-height:62px;padding:9px 12px;overflow:visible;background:linear-gradient(180deg,rgba(17,45,96,.98),rgba(17,45,96,.92))}",
)
replace_once(
    "frontend/src/layouts/AppShell.vue",
    ".school-brand-logo{flex:0 0 auto;width:46px;height:46px;object-fit:contain}",
    ".school-brand-logo{position:relative;z-index:9;flex:0 0 auto;width:46px;height:46px;object-fit:contain;filter:drop-shadow(0 3px 8px rgba(0,0,0,.22))}",
)

# Platform shell is independent from school branding and uses exact route highlighting.
write(
    "frontend/src/layouts/PlatformLayout.vue",
    """<script setup lang=\"ts\">\nimport { useRouter } from 'vue-router'\nimport { usePlatformSession } from '../platform/session'\n\nconst router = useRouter()\nconst { state, clearSession } = usePlatformSession()\nconst institutionName = String(import.meta.env.VITE_INSTITUTION_NAME || '示例大学')\nconst operatorName = String(import.meta.env.VITE_OPERATOR_NAME || '运营单位信息待填写')\nconst icpRecord = String(import.meta.env.VITE_ICP_RECORD || 'ICP备案信息待填写')\n\nfunction logout() {\n  clearSession()\n  void router.replace('/platform/login')\n}\n</script>\n\n<template>\n  <div class=\"platform-shell\">\n    <aside class=\"platform-nav\">\n      <div class=\"platform-brand\"><strong>系统服务管理</strong><span>统一运维平台</span></div>\n      <nav>\n        <RouterLink to=\"/platform\" exact-active-class=\"custom-active\">服务概览</RouterLink>\n        <RouterLink to=\"/platform/plans\" exact-active-class=\"custom-active\">套餐管理</RouterLink>\n        <RouterLink to=\"/platform/subscription\" exact-active-class=\"custom-active\">服务订阅</RouterLink>\n        <RouterLink to=\"/platform/features\" exact-active-class=\"custom-active\">功能授权</RouterLink>\n        <RouterLink to=\"/platform/quotas\" exact-active-class=\"custom-active\">资源配额</RouterLink>\n        <RouterLink to=\"/platform/audit\" exact-active-class=\"custom-active\">操作审计</RouterLink>\n        <RouterLink to=\"/platform/profile/password\" exact-active-class=\"custom-active\">修改密码</RouterLink>\n      </nav>\n      <div class=\"platform-nav-foot\"><button type=\"button\" class=\"logout\" @click=\"logout\">退出登录</button><footer><span>{{ operatorName }}</span><span>{{ icpRecord }}</span></footer></div>\n    </aside>\n    <main class=\"platform-main\">\n      <header><div><strong>{{ state.user?.displayName }}</strong><span>{{ institutionName }}系统服务管理</span></div><span class=\"single-customer-badge\">单客户运行</span></header>\n      <RouterView />\n    </main>\n  </div>\n</template>\n\n<style scoped>\n.platform-shell{min-height:100vh;background:#f3f6fb;color:#172033}.platform-nav{position:fixed;inset:0 auto 0 0;z-index:30;display:flex;flex-direction:column;width:252px;height:100vh;padding:24px 18px;overflow:hidden;color:#eaf0ff;background:linear-gradient(180deg,#102c64,#0f2148)}.platform-brand{display:grid;gap:5px;padding:4px 4px 22px;border-bottom:1px solid rgba(255,255,255,.12)}.platform-brand strong{font-size:1.08rem}.platform-brand span{color:#aebfe5;font-size:.76rem;font-weight:700;letter-spacing:.08em}nav{display:grid;gap:7px;min-height:0;margin-top:22px;overflow-y:auto}a{padding:11px 13px;border-radius:11px;color:#b9c8e9;text-decoration:none;transition:.18s ease}a:hover,a.custom-active{color:#fff;background:rgba(255,255,255,.11)}.platform-nav-foot{display:grid;gap:14px;flex:0 0 auto;margin-top:auto}.logout{min-height:42px;border:1px solid rgba(255,255,255,.18);border-radius:11px;color:#dbe6ff;background:transparent;cursor:pointer}footer{display:grid;gap:4px;padding-top:12px;border-top:1px solid rgba(255,255,255,.12);color:#91a8d5;font-size:.66rem;line-height:1.45}.platform-main{min-width:0;min-height:100vh;margin-left:252px;padding:28px clamp(22px,4vw,56px) 70px}.platform-main>header{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:28px;padding:16px 20px;border:1px solid #dde4ef;border-radius:16px;background:rgba(255,255,255,.88);box-shadow:0 12px 28px rgba(22,43,82,.06)}.platform-main>header div{display:grid;gap:4px}.platform-main>header strong{font-size:1rem}.platform-main>header span{color:#69758b;font-size:.76rem}.single-customer-badge{padding:7px 11px;border-radius:999px;color:#17664f!important;background:#e8f8f2;font-weight:700}@media(max-width:800px){.platform-nav{position:static;width:auto;height:auto;overflow:visible}.platform-main{margin-left:0}.platform-shell{display:block}}\n</style>\n""",
)

# Map every active quota code to a business title.
replace_once(
    "frontend/src/views/platform/PlatformDashboardView.vue",
    "return ({ MAX_STUDENTS: '学生容量', MAX_ROOMS: '宿舍房间', MAX_BEDS: '床位容量', MAX_ACTIVE_BATCHES: '同时开放批次', MAX_ADMIN_USERS: '管理员账号' } as Record<string, string>)[String(code)] ?? '资源项目'",
    "return ({ MAX_ADMIN_USERS: '管理员账号', MAX_STUDENTS: '学生容量', MAX_CAMPUSES: '校区数量', MAX_BUILDINGS: '宿舍楼栋', MAX_ROOMS: '宿舍房间', MAX_BEDS: '床位容量', MAX_BATCHES_PER_YEAR: '年度选寝批次', MAX_CONCURRENT_ACTIVE_BATCHES: '同时开放批次' } as Record<string, string>)[String(code)] ?? String(code ?? '未命名资源')",
)

# Merge residency and declaration review into one page, and add a dropdown filter.
replace_once(
    "frontend/src/views/admin/AdminBedConfirmationView.vue",
    "import { bedTypeLabel } from '../../utils/bedLabels'",
    "import { bedTypeLabel } from '../../utils/bedLabels'\n\nwithDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })",
)
replace_once(
    "frontend/src/views/admin/AdminBedConfirmationView.vue",
    "const keyword = ref('')",
    "const keyword = ref('')\nconst reviewFilter = ref('ALL')",
)
replace_once(
    "frontend/src/views/admin/AdminBedConfirmationView.vue",
    "const readyCount = computed(() => students.value.filter(item => item.review_state === 'READY').length)",
    "const readyCount = computed(() => students.value.filter(item => item.review_state === 'READY').length)\nconst filteredRooms = computed(() => rooms.value.filter((room) => {\n  if (reviewFilter.value === 'CONFLICT') return Number(room.conflict_count ?? 0) > 0\n  if (reviewFilter.value === 'READY') return Number(room.pending_count ?? 0) > Number(room.conflict_count ?? 0)\n  if (reviewFilter.value === 'EMPTY') return Number(room.pending_count ?? 0) === 0\n  return true\n}))",
)
replace_once(
    "frontend/src/views/admin/AdminBedConfirmationView.vue",
    "    <header class=\"page-title split-title\"><div><span class=\"eyebrow\">ACTUAL BED REVIEW</span><h2>按寝室核查实际床位</h2><p>学生自主申报不会直接修改正式床位。进入寝室后核对全寝室申报，可一次通过全部无冲突记录。</p></div><button class=\"button secondary\" @click=\"loadRooms\">刷新</button></header>",
    "    <header v-if=\"!embedded\" class=\"page-title split-title\"><div><span class=\"eyebrow\">ACTUAL BED REVIEW</span><h2>按寝室核查实际床位</h2><p>学生自主申报不会直接修改正式床位。进入寝室后核对全寝室申报，可一次通过全部无冲突记录。</p></div><button class=\"button secondary\" @click=\"loadRooms\">刷新</button></header>",
)
replace_once(
    "frontend/src/views/admin/AdminBedConfirmationView.vue",
    "    <section class=\"panel room-search\"><input v-model.trim=\"keyword\" class=\"input\" placeholder=\"搜索楼栋或寝室号\" @keyup.enter=\"loadRooms\"><button class=\"button primary\" @click=\"loadRooms\">查询</button></section>",
    "    <section class=\"panel room-search\"><input v-model.trim=\"keyword\" class=\"input\" placeholder=\"搜索楼栋或寝室号\" @keyup.enter=\"loadRooms\"><select v-model=\"reviewFilter\" class=\"input\"><option value=\"ALL\">全部核查状态</option><option value=\"READY\">存在可直接通过记录</option><option value=\"CONFLICT\">存在冲突记录</option><option value=\"EMPTY\">暂无待核查记录</option></select><button class=\"button primary\" @click=\"loadRooms\">查询</button></section>",
)
replace_once(
    "frontend/src/views/admin/AdminBedConfirmationView.vue",
    "<div v-else class=\"review-room-grid\"><article v-for=\"room in rooms\"",
    "<div v-else class=\"review-room-grid\"><article v-for=\"room in filteredRooms\"",
)
replace_once(
    "frontend/src/views/admin/AdminBedConfirmationView.vue",
    ".room-search{display:grid;grid-template-columns:1fr auto;gap:10px}",
    ".room-search{display:grid;grid-template-columns:minmax(220px,1fr) 220px auto;gap:10px}",
)

replace_once(
    "frontend/src/views/admin/AdminResidencyView.vue",
    "import { bedTypeLabel } from '../../utils/bedLabels'",
    "import { bedTypeLabel } from '../../utils/bedLabels'\nimport AdminBedConfirmationView from './AdminBedConfirmationView.vue'",
)
replace_once(
    "frontend/src/views/admin/AdminResidencyView.vue",
    "const items = ref<DataObject[]>([])",
    "const residencyTab = ref<'RESIDENCY' | 'DECLARATION'>('RESIDENCY')\nconst items = ref<DataObject[]>([])",
)
replace_once(
    "frontend/src/views/admin/AdminResidencyView.vue",
    "    <div class=\"page-title\"><span class=\"eyebrow\">RESIDENCY TRUTH</span><h2>在住与实际床位确认</h2><p>批次结束不会释放在住状态。选寝室模式下未确认的实际床位在此核对；存在待确认学生的寝室不能重新开放选床模式。</p></div>\n    <p v-if=\"error\" class=\"alert error\">{{ error }}</p><p v-if=\"message\" class=\"alert success\">{{ message }}</p>",
    "    <div class=\"page-title\"><span class=\"eyebrow\">RESIDENCY AND BED REVIEW</span><h2>在住与床位核查</h2><p>统一维护正式在住关系、管理员床位确认和学生实际床位申报，避免两个页面重复处理同一业务。</p></div>\n    <div class=\"residency-tabs\"><button class=\"button\" :class=\"residencyTab === 'RESIDENCY' ? 'primary' : 'ghost'\" @click=\"residencyTab = 'RESIDENCY'\">在住名单与管理员确认</button><button class=\"button\" :class=\"residencyTab === 'DECLARATION' ? 'primary' : 'ghost'\" @click=\"residencyTab = 'DECLARATION'\">学生申报核查</button></div>\n    <template v-if=\"residencyTab === 'RESIDENCY'\">\n    <p v-if=\"error\" class=\"alert error\">{{ error }}</p><p v-if=\"message\" class=\"alert success\">{{ message }}</p>",
)
content = read("frontend/src/views/admin/AdminResidencyView.vue")
needle = "    <div v-if=\"selected\" class=\"modal-overlay\""
idx = content.index(needle)
# Close the residency-only template immediately before the dialogs, then render declarations outside it.
content = content[:idx] + "    </template>\n    <AdminBedConfirmationView v-else embedded />\n\n" + content[idx:]
write("frontend/src/views/admin/AdminResidencyView.vue", content)
replace_once(
    "frontend/src/views/admin/AdminResidencyView.vue",
    ".residency-end-dialog{width:min(540px,calc(100vw - 32px));padding:24px}",
    ".residency-tabs{display:flex;gap:10px;flex-wrap:wrap}.residency-end-dialog{width:min(540px,calc(100vw - 32px));padding:24px}",
)

# Remove the standalone route and add school-admin password management.
replace_once(
    "frontend/src/router/index.ts",
    "        { path: 'admin/bed-confirmations', name: 'admin-bed-confirmations', component: () => import('../views/admin/AdminBedConfirmationView.vue'), meta: { role: 'ADMIN' } },\n",
    "",
)
replace_once(
    "frontend/src/router/index.ts",
    "        { path: 'admin/anomalies', name: 'admin-anomalies', component: () => import('../views/admin/AdminAnomalyWorkbenchView.vue'), meta: { role: 'ADMIN' } },",
    "        { path: 'admin/anomalies', name: 'admin-anomalies', component: () => import('../views/admin/AdminAnomalyWorkbenchView.vue'), meta: { role: 'ADMIN' } },\n        { path: 'admin/profile/password', name: 'admin-password', component: () => import('../views/admin/AdminPasswordView.vue'), meta: { role: 'ADMIN' } },",
)

# One welcome contract: locale messages only, no legacy single message.
replace_once(
    "backend-java/model/src/main/resources/auth/openapi-auth.yaml",
    "        message:\n          type: string\n          description: 兼容旧客户端的中文欢迎语\n",
    "",
)
replace_once(
    "backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java",
    "        String selectedTemplate = countryTemplate;\n        if (selectedTemplate == null || selectedTemplate.isBlank()) {\n            selectedTemplate = \"CN\".equals(countryCode)\n                    ? configuration.messages().get(\"zh-CN\")\n                    : configuration.messages().get(\"en-US\");\n        }\n        if (selectedTemplate == null || selectedTemplate.isBlank()) {\n            selectedTemplate = configuration.messages().get(\"en-US\");\n        }\n        String selected = render(selectedTemplate, variables);\n\n",
    "",
)
replace_once(
    "backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java",
    "        data.setMessages(renderedMessages);\n        data.setMessage(selected);",
    "        data.setMessages(renderedMessages);",
)

# Add school-admin password endpoint and policy feedback.
replace_once(
    "backend-java/model/src/main/resources/auth/openapi-auth.yaml",
    "  /api/v1/auth/me:\n",
    "  /api/v1/auth/password:\n    put:\n      tags: [Auth]\n      operationId: changePassword\n      summary: 修改当前学校管理员密码\n      security: [{ bearerAuth: [] }]\n      requestBody:\n        required: true\n        content:\n          application/json:\n            schema: { $ref: '#/components/schemas/ChangePasswordRequest' }\n      responses:\n        '200':\n          description: 修改成功\n          content:\n            application/json:\n              schema:\n                $ref: '../common-response/openapi-common-response.yaml#/components/schemas/VoidSuccessResponse'\n        '400': { $ref: '#/components/responses/ErrorResponse' }\n        '401': { $ref: '#/components/responses/ErrorResponse' }\n        '403': { $ref: '#/components/responses/ErrorResponse' }\n\n  /api/v1/auth/me:\n",
)
replace_once(
    "backend-java/model/src/main/resources/auth/openapi-auth.yaml",
    "    ActivateRequest:\n      type: object\n      required: [studentNumber, studentName, password]\n      properties:\n        studentNumber: { type: string, pattern: '^\\d{12}$' }\n        studentName: { type: string, minLength: 1, maxLength: 128 }\n        password: { type: string, minLength: 8, maxLength: 72 }\n\n",
    "    ActivateRequest:\n      type: object\n      required: [studentNumber, studentName, password]\n      properties:\n        studentNumber: { type: string, pattern: '^\\d{12}$' }\n        studentName: { type: string, minLength: 1, maxLength: 128 }\n        password: { type: string, minLength: 8, maxLength: 72 }\n\n    ChangePasswordRequest:\n      type: object\n      required: [currentPassword, newPassword]\n      properties:\n        currentPassword: { type: string, minLength: 1, maxLength: 72 }\n        newPassword: { type: string, minLength: 12, maxLength: 72 }\n\n",
)
replace_once(
    "backend-java/server/src/main/java/com/wust/dormitory/auth/AuthService.java",
    "    public record LoginResult(String accessToken, long expiresInSeconds, CurrentUser user) {\n    }",
    "    @Transactional\n    public void changePassword(CurrentUser user, String currentPassword, String newPassword) {\n        if (user == null || !\"ADMIN\".equals(user.userType())) {\n            throw new BusinessException(\"ADMIN_PASSWORD_FORBIDDEN\", \"只有学校管理员可以在此修改密码\", HttpStatus.FORBIDDEN);\n        }\n        if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 72\n                || !newPassword.matches(\".*[A-Z].*\")\n                || !newPassword.matches(\".*[a-z].*\")\n                || !newPassword.matches(\".*[0-9].*\")\n                || !newPassword.matches(\".*[^A-Za-z0-9].*\")) {\n            throw new BusinessException(\"PASSWORD_POLICY_INVALID\",\n                    \"新密码需为12至72位，并同时包含大写字母、小写字母、数字和特殊字符\");\n        }\n        String hash = jdbc.queryForObject(\"SELECT password_hash FROM app_user WHERE id=:id\",\n                Map.of(\"id\", user.userId()), String.class);\n        if (hash == null || currentPassword == null || !passwordEncoder.matches(currentPassword, hash)) {\n            throw new BusinessException(\"CURRENT_PASSWORD_INVALID\", \"当前密码不正确\", HttpStatus.UNAUTHORIZED);\n        }\n        jdbc.update(\"UPDATE app_user SET password_hash=:hash, password_change_required=0 WHERE id=:id\",\n                new MapSqlParameterSource().addValue(\"hash\", passwordEncoder.encode(newPassword))\n                        .addValue(\"id\", user.userId()));\n        tokenService.revokeUser(user.userId());\n    }\n\n    public record LoginResult(String accessToken, long expiresInSeconds, CurrentUser user) {\n    }",
)
replace_once(
    "backend-java/server/src/main/java/com/wust/dormitory/auth/AuthController.java",
    "import com.wust.dormitory.model.dto.CurrentUserData;",
    "import com.wust.dormitory.model.dto.ChangePasswordRequest;\nimport com.wust.dormitory.model.dto.CurrentUserData;",
)
replace_once(
    "backend-java/server/src/main/java/com/wust/dormitory/auth/AuthController.java",
    "    @Override\n    public ResponseEntity<CurrentUserSuccessResponse> getCurrentUser() {",
    "    @Override\n    public ResponseEntity<VoidSuccessResponse> changePassword(ChangePasswordRequest request) {\n        authService.changePassword(SecurityUsers.current(), request.getCurrentPassword(), request.getNewPassword());\n        return ResponseEntity.ok(ResponseFactory.empty());\n    }\n\n    @Override\n    public ResponseEntity<CurrentUserSuccessResponse> getCurrentUser() {",
)

write(
    "frontend/src/views/admin/AdminPasswordView.vue",
    """<script setup lang=\"ts\">\nimport { computed, ref } from 'vue'\nimport { useRouter } from 'vue-router'\nimport { api } from '../../api/client'\nimport { useAuthStore } from '../../stores/auth'\nimport { useI18n } from '../../i18n'\n\nconst router = useRouter()\nconst auth = useAuthStore()\nconst { translateError } = useI18n()\nconst currentPassword = ref('')\nconst newPassword = ref('')\nconst confirmPassword = ref('')\nconst error = ref('')\nconst saving = ref(false)\nconst rules = computed(() => ({\n  length: newPassword.value.length >= 12 && newPassword.value.length <= 72,\n  upper: /[A-Z]/.test(newPassword.value),\n  lower: /[a-z]/.test(newPassword.value),\n  digit: /[0-9]/.test(newPassword.value),\n  special: /[^A-Za-z0-9]/.test(newPassword.value),\n}))\nconst validPassword = computed(() => Object.values(rules.value).every(Boolean))\n\nasync function submit() {\n  error.value = ''\n  if (!validPassword.value) { error.value = '新密码需为12至72位，并同时包含大写字母、小写字母、数字和特殊字符。'; return }\n  if (newPassword.value !== confirmPassword.value) { error.value = '两次输入的新密码不一致。'; return }\n  saving.value = true\n  try {\n    await api.put('/api/v1/auth/password', { currentPassword: currentPassword.value, newPassword: newPassword.value })\n    await auth.logout()\n    await router.replace('/login')\n  } catch (reason) { error.value = translateError(reason) }\n  finally { saving.value = false }\n}\n</script>\n\n<template>\n  <div class=\"content-column narrow\">\n    <div class=\"page-title\"><span class=\"eyebrow\">ACCOUNT SECURITY</span><h2>修改管理员密码</h2><p>修改成功后全部登录令牌立即失效，需要使用新密码重新登录。</p></div>\n    <section class=\"panel password-card\"><form class=\"form-stack\" @submit.prevent=\"submit\"><label><span>当前密码</span><input v-model=\"currentPassword\" class=\"input\" type=\"password\" autocomplete=\"current-password\" required /></label><label><span>新密码</span><input v-model=\"newPassword\" class=\"input\" type=\"password\" autocomplete=\"new-password\" required /></label><div class=\"password-rules\"><strong>密码要求</strong><span :class=\"{ pass: rules.length }\">12至72位</span><span :class=\"{ pass: rules.upper }\">包含大写字母</span><span :class=\"{ pass: rules.lower }\">包含小写字母</span><span :class=\"{ pass: rules.digit }\">包含数字</span><span :class=\"{ pass: rules.special }\">包含特殊字符</span></div><label><span>确认新密码</span><input v-model=\"confirmPassword\" class=\"input\" type=\"password\" autocomplete=\"new-password\" required /></label><p v-if=\"error\" class=\"alert error\">{{ error }}</p><button class=\"button primary\" :disabled=\"saving\">{{ saving ? '正在修改…' : '修改密码并重新登录' }}</button></form></section>\n  </div>\n</template>\n\n<style scoped>.password-card{padding:24px}.password-rules{display:flex;gap:8px;flex-wrap:wrap;padding:12px;border-radius:12px;background:var(--soft)}.password-rules strong{width:100%}.password-rules span{padding:4px 8px;border-radius:999px;color:#9b2838;background:#fff0f2;font-size:12px}.password-rules span.pass{color:#17664f;background:#e8f8f2}</style>\n""",
)

# Remove the one-shot patch artifacts from the resulting commit.
path("scripts/ci/apply_ui_business_closure.py").unlink()
path(".github/workflows/agent-ui-business-closure.yml").unlink()
