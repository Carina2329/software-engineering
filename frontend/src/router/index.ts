import { createRouter, createWebHashHistory } from 'vue-router'

/**
 * 路由表：页面按角色放在 views/ 对应目录下，路由 path 小写连字符。
 * 登录鉴权完成后在这里加路由守卫。
 */
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'Home',
      component: () => import('../views/common/TestPage.vue'),
    },
  ],
})

export default router
