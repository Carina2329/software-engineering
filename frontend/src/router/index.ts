import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
    title?: string
  }
}

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../views/common/AuthHomePage.vue'),
      meta: {
        requiresAuth: true,
        title: '账号中心',
      },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/common/LoginPage.vue'),
      meta: {
        guestOnly: true,
        title: '登录',
      },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/common/RegisterPage.vue'),
      meta: {
        guestOnly: true,
        title: '注册',
      },
    },
    {
      path: '/password-reset',
      name: 'password-reset',
      component: () => import('../views/common/PasswordResetPage.vue'),
      meta: {
        guestOnly: true,
        title: '找回密码',
      },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.ensureInitialized()

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (to.meta.guestOnly && auth.isAuthenticated) {
    return { name: 'home' }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} · 学科竞赛平台`
    : '学科竞赛平台'
})

export default router
