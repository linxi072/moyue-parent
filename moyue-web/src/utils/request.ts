import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

// 后端统一以 HTTP 200 承载业务码（架构说明书 11.2）：
// code = 0 成功，其余为业务错误；HTTP 状态码只表达协议层问题。
export interface R<T = any> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T = any> {
  total: number
  records: T[]
}

export const SUCCESS_CODE = 0

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 30000
})

instance.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = 'Bearer ' + userStore.token
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const body = response.data as R
    // 非 JSON（如 CSV / Zip / OpenAPI 原文）直接透传原始响应
    if (typeof body !== 'object' || body === null || body.code === undefined) {
      return response as any
    }
    if (body.code !== SUCCESS_CODE) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    // 直接返回 data，业务层不再层层解包
    return body.data as any
  },
  (error) => {
    const status = error?.response?.status
    const body = error?.response?.data as R | undefined
    if (status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      ElMessage.error('登录已失效，请重新登录')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    if (status === 403) {
      ElMessage.error(body?.message || '无权限访问该资源')
      return Promise.reject(error)
    }
    if (status === 429) {
      ElMessage.error('请求过于频繁，请稍后再试')
      return Promise.reject(error)
    }
    ElMessage.error(body?.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

/**
 * 解包后的请求门面。
 *
 * <p>axios 原生签名返回 Promise<AxiosResponse<T>>，但响应拦截器已经把 body.data 提前解出，
 * 运行时类型与声明类型不一致。这里覆写签名，让调用方拿到真正的业务类型 T：
 * <pre>
 *   const users = await request.get&lt;PageResult&lt;SysUser&gt;&gt;(url)   // 直接用，不用 .data
 * </pre>
 */
export interface Http {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
  request<T = any>(config: AxiosRequestConfig): Promise<T>
}

const request = instance as unknown as Http

/**
 * 原始 axios 实例。
 * 仅用于「响应不是统一响应体包装」的场景（OpenAPI 原文等），其余一律用 request。
 */
export const http = instance

/** 导出类请求：期望拿到原始 Blob（CSV / Zip） */
export async function requestRaw(config: AxiosRequestConfig): Promise<Blob> {
  const resp: any = await instance.request({ ...config, responseType: 'blob' })
  return (resp?.data ?? resp) as Blob
}

export default request
