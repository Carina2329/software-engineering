import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'
import { AUTH_EXPIRED_EVENT } from './auth/events'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

let lastExpiredNotice = 0
window.addEventListener(AUTH_EXPIRED_EVENT, () => {
  const auth = useAuthStore(pinia)
  auth.clearSessionState()

  const now = Date.now()
  if (now - lastExpiredNotice > 2_000) {
    lastExpiredNotice = now
    ElMessage.warning('登录状态已过期，请重新登录')
  }

  const currentRoute = router.currentRoute.value
  if (currentRoute.name !== 'login') {
    void router.replace({
      name: 'login',
      query: {
        redirect: currentRoute.fullPath,
        reason: 'expired',
      },
    })
  }
})

app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
