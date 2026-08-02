import type { RouteRecordRaw } from 'vue-router'
import { usePlatformSession } from './session'

export const platformRoutes: RouteRecordRaw[] = [
  {
    path: '/platform/login',
    name: 'platform-login',
    component: () => import('../views/platform/PlatformLoginView.vue'),
    meta: { platformPublic: true },
  },
  {
    path: '/platform',
    component: () => import('../layouts/PlatformLayout.vue'),
    children: [
      { path: '', name: 'platform-dashboard', component: () => import('../views/platform/PlatformDashboardView.vue') },
      { path: 'plans', name: 'platform-plans', component: () => import('../views/platform/PlatformPlansView.vue') },
      { path: 'subscription', name: 'platform-subscription', component: () => import('../views/platform/PlatformSubscriptionView.vue') },
      { path: 'features', name: 'platform-features', component: () => import('../views/platform/PlatformFeaturesView.vue') },
      { path: 'quotas', name: 'platform-quotas', component: () => import('../views/platform/PlatformQuotasView.vue') },
      { path: 'audit', name: 'platform-audit', component: () => import('../views/platform/PlatformAuditView.vue') },
      { path: 'profile/password', name: 'platform-password', component: () => import('../views/platform/PlatformPasswordView.vue'), meta: { allowPasswordChange: true } },
    ],
  },
]

export function installPlatformRouteGuard(router: import('vue-router').Router): void {
  router.beforeEach((to) => {
    if (!to.path.startsWith('/platform')) return true
    if (to.meta.platformPublic) return true
    const { authenticated, passwordChangeRequired } = usePlatformSession()
    if (!authenticated.value) return { name: 'platform-login' }
    if (passwordChangeRequired.value && !to.meta.allowPasswordChange) {
      return { name: 'platform-password' }
    }
    return true
  })
}
