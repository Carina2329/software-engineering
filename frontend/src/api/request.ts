import axios from 'axios'

/**
 * Axios 统一封装：全项目请求必须走这里，禁止页面内新建 Axios 实例。
 * 约定后端统一返回 { code, message, data }，code === 0 为成功。
 */
const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

// 请求拦截：自动携带 token（登录模块完成后写入 localStorage）
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一拆包 Result，code 非 0 统一走错误处理
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 0) {
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  (error) => {
    return Promise.reject(error)
  },
)

export default request
