// ====================================================================
// 通用API请求封装 - 所有模块共用
// 统一baseURL、Token注入、错误处理、Result<T>解包
// ====================================================================
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截：自动加Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截：统一处理Result<T>和错误码
request.interceptors.response.use(
  res => {
    const data = res.data
    // 后端统一返回 { code, message, data }
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 200) {
        return data.data
      }
      // 业务错误
      ElMessage.error(data.message || '操作失败')
      return Promise.reject(new Error(data.message || '操作失败'))
    }
    return data
  },
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('permissions')
      window.location.href = '/login'
    } else {
      const msg = err.response?.data?.message || err.message || '请求失败'
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export default request
