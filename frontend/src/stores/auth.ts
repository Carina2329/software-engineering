import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 登录状态：登录模块完成后补充 login/logout 与用户信息。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') ?? '')

  const setToken = (value: string) => {
    token.value = value
    localStorage.setItem('token', value)
  }

  const clearToken = () => {
    token.value = ''
    localStorage.removeItem('token')
  }

  return { token, setToken, clearToken }
})
