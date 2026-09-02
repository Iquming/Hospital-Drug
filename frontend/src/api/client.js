import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8081',
  timeout: 8000
})

let authToken = localStorage.getItem('hospitalDrugToken') || ''
let unauthorizedHandler = () => {}

api.interceptors.request.use(config => {
  if (authToken) config.headers.Authorization = `Bearer ${authToken}`
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) unauthorizedHandler()
    return Promise.reject(error)
  }
)

export const setAuthToken = token => {
  authToken = token || ''
}

export const setUnauthorizedHandler = handler => {
  unauthorizedHandler = typeof handler === 'function' ? handler : () => {}
}

export const errorMessage = (error, fallback = '操作失败') => (
  error?.response?.data?.message
  || (typeof error?.response?.data === 'string' ? error.response.data : '')
  || (error?.request ? '无法连接服务，请检查后端是否启动' : '')
  || error?.message
  || fallback
)

export default api
