import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({ baseURL: '/api', timeout: 15000 })

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('edu_token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

request.interceptors.response.use(
  async (resp) => {
    // 兼容blob下载等场景
    if (resp.config.responseType === 'blob') return resp
    // 兼容历史void接口：空响应体直接视为成功
    if (resp.data === '' || resp.data === null || resp.data === undefined) return resp.data
    const { code, msg, data } = resp.data
    if (code === 200) return data
    if (code === 401) {
      // 同时清空内存态，否则路由守卫仍认为已登录，会在 /login 与 /home 间死循环
      const { useUserStore } = await import('../store/user')
      useUserStore().logout()
      router.push('/login')
    }
    ElMessage.error(msg || '请求失败')
    return Promise.reject(new Error(msg))
  },
  (err) => {
    // silent 标记的请求（如头像探测）不弹错误提示
    if (!err.config?.silent) ElMessage.error(err.message || '网络异常')
    return Promise.reject(err)
  }
)

export default request
