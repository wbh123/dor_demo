import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { pinia } from '../stores'
import { platformRoutes, installPlatformRouteGuard } from '../platform/routes'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    ...platformRoutes,
    {
      path: '/',
      component: () => import('../layouts/AppShell.vue'),
      children: [
        { path: '', redirect: '/student' },
        { path: 'student', name: 'student-home', component: () => import('../views/student/StudentHomeView.vue'), meta: { role: 'STUDENT' } },
        { path: 'student/preferences', name: 'student-preferences', component: () => import('../views/student/QuestionnaireView.vue'), meta: { role: 'STUDENT' } },
        { path: 'student/batches/:batchId/questionnaire', name: 'questionnaire', component: () => import('../views/student/QuestionnaireView.vue'), meta: { role: 'STUDENT' } },
        { path: 'student/batches/:batchId/rooms', name: 'room-list', component: () => import('../views/student/RoomListView.vue'), meta: { role: 'STUDENT' } },
        { path: 'student/batches/:batchId/rooms/:roomId', name: 'room-detail', component: () => import('../views/student/RoomDetailView.vue'), meta: { role: 'STUDENT' } },
        { path: 'student/teams', name: 'student-teams', component: () => import('../views/student/TeamView.vue'), meta: { role: 'STUDENT' } },
        { path: 'student/room-change', name: 'student-room-change', component: () => import('../views/student/StudentRoomChangeView.vue'), meta: { role: 'STUDENT' } },
        { path: 'student/batches/:batchId/assignment', name: 'student-assignment', component: () => import('../views/student/AssignmentView.vue'), meta: { role: 'STUDENT' } },
        { path: 'admin', name: 'admin-dashboard', component: () => import('../views/admin/AdminDashboardView.vue'), meta: { role: 'ADMIN' } },
        { path: 'admin/data', name: 'admin-data', component: () => import('../views/admin/AdminDataView.vue'), meta: { role: 'ADMIN' } },
        { path: 'admin/dormitories', name: 'admin-dormitories', component: () => import('../views/admin/AdminDormitoryView.vue'), meta: { role: 'ADMIN' } },
        { path: 'admin/residencies', name: 'admin-residencies', component: () => import('../views/admin/AdminResidencyView.vue'), meta: { role: 'ADMIN' } },
        { path: 'admin/matching', name: 'admin-matching', component: () => import('../views/admin/AdminMatchingView.vue'), meta: { role: 'ADMIN' } },
        { path: 'admin/rule-templates', name: 'admin-rule-templates', component: () => import('../views/admin/AdminRuleTemplateView.vue'), meta: { role: 'ADMIN' } },
        { path: 'admin/batches', name: 'admin-batches', component: () => import('../views/admin/AdminBatchView.vue'), meta: { role: 'ADMIN' } },
        { path: 'admin/assignments', name: 'admin-assignments', component: () => import('../views/admin/AdminAssignmentView.vue'), meta: { role: 'ADMIN' } },
        { path: 'admin/room-change', name: 'admin-room-change', component: () => import('../views/admin/AdminRoomChangeView.vue'), meta: { role: 'ADMIN' } },
        { path: 'admin/operations', name: 'admin-operations', component: () => import('../views/admin/AdminOperationsView.vue'), meta: { role: 'ADMIN' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

installPlatformRouteGuard(router)

let restored = false
router.beforeEach(async (to) => {
  if (to.path.startsWith('/platform')) return true
  const auth = useAuthStore(pinia)
  if (!restored) { restored = true; await auth.restore() }
  if (to.meta.public) {
    if (auth.authenticated) return auth.isAdmin ? '/admin' : '/student'
    return true
  }
  if (!auth.authenticated) return '/login'
  const requiredRole = to.meta.role as string | undefined
  if (requiredRole && auth.user?.userType !== requiredRole) return auth.isAdmin ? '/admin' : '/student'
  if (to.path === '/' || (to.path === '/student' && auth.isAdmin)) return auth.isAdmin ? '/admin' : '/student'
  return true
})

export default router
