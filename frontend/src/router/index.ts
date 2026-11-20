import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
    permissions?: string[]
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
      path: '/admin',
      component: () => import('../layouts/AdminLayout.vue'),
      meta: {
        requiresAuth: true,
        title: '权限管理',
      },
      children: [
        {
          path: '',
          name: 'admin-entry',
          component: () => import('../views/admin/AdminEntryPage.vue'),
          meta: {
            requiresAuth: true,
            title: '管理后台',
          },
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('../views/admin/UserManagementPage.vue'),
          meta: {
            requiresAuth: true,
            permissions: ['system:user:list'],
            title: '用户管理',
          },
        },
        {
          path: 'roles',
          name: 'admin-roles',
          component: () => import('../views/admin/RoleManagementPage.vue'),
          meta: {
            requiresAuth: true,
            permissions: ['system:role:list'],
            title: '角色管理',
          },
        },
        {
          path: 'menus',
          name: 'admin-menus',
          component: () => import('../views/admin/PermissionResourcePage.vue'),
          meta: {
            requiresAuth: true,
            permissions: ['system:menu:list'],
            title: '权限资源',
          },
        },
        {
          path: 'organizations',
          name: 'admin-organizations',
          component: () => import('../views/admin/OrganizationManagementPage.vue'),
          meta: {
            requiresAuth: true,
            permissions: ['organization:list'],
            title: '组织与学校',
          },
        },
        {
          path: 'school-affiliations',
          name: 'admin-school-affiliations',
          component: () => import('../views/admin/SchoolAffiliationReviewPage.vue'),
          meta: {
            requiresAuth: true,
            permissions: ['school:affiliation:list'],
            title: '学校归属审核',
          },
        },
      ],
    },
    {
      path: '/schools',
      name: 'schools',
      component: () => import('../views/common/SchoolDirectoryPage.vue'),
      meta: {
        requiresAuth: true,
        permissions: ['school:list'],
        title: '学校目录',
      },
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('../views/common/ForbiddenPage.vue'),
      meta: {
        requiresAuth: true,
        title: '无权访问',
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

  // 登录、注册、找回密码等公开页面不主动恢复旧会话。
  // 否则用户明确打开登录页时，会被 Refresh Cookie 再次送回旧账号页面。
  if (!to.meta.guestOnly) {
    await auth.ensureInitialized()
  }

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

  const requiredPermissions = to.meta.permissions ?? []
  if (requiredPermissions.some((permission) => !auth.can(permission))) {
    return {
      name: 'forbidden',
      query: {
        from: to.fullPath,
      },
    }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} · 学科竞赛平台`
    : '学科竞赛平台'
})

export default router
