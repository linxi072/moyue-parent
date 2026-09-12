import axios, { type AxiosInstance } from 'axios';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';

/** 统一响应体结构（对齐后端 R<T>） */
export interface ApiResult<T = unknown> {
  code: number;
  message: string;
  data: T;
  traceId: string;
}

// Axios 实例：baseURL 指向 /api/v1（经 Vite 代理打到网关 8080）
const request: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
});

// 请求拦截：注入 Authorization
request.interceptors.request.use((config) => {
  const token = useUserStore().token;
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

// 响应拦截：统一解 R<T>，业务错误码统一处理
request.interceptors.response.use(
  (response: any) => {
    const res: ApiResult = response.data;
    if (res.code === 0) {
      // 返回 ApiResult，由下方 get/post 取 .data
      return res;
    }
    // 未登录 / 无权限 -> 清除登录态并跳登录
    if (res.code === 10002 || res.code === 10003) {
      ElMessage.error(res.message || '登录已过期，请重新登录');
      useUserStore().logout();
      window.location.href = '/login';
      return Promise.reject(new Error(res.message || 'unauthorized'));
    }
    ElMessage.error(res.message || '请求失败');
    return Promise.reject(new Error(res.message || 'business error'));
  },
  (error: any) => {
    if (error.response && error.response.status === 401) {
      useUserStore().logout();
      window.location.href = '/login';
    }
    const msg =
      (error.response && error.response.data && error.response.data.message) ||
      error.message ||
      '网络异常';
    ElMessage.error(msg);
    return Promise.reject(error);
  }
);

/** GET 请求：直接返回 data 载荷 */
export function get<T = unknown>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request.get(url, { params }).then((r: any) => r.data as T);
}

/** POST 请求：直接返回 data 载荷 */
export function post<T = unknown>(url: string, data?: unknown): Promise<T> {
  return request.post(url, data).then((r: any) => r.data as T);
}

/** PUT 请求：直接返回 data 载荷 */
export function put<T = unknown>(url: string, data?: unknown): Promise<T> {
  return request.put(url, data).then((r: any) => r.data as T);
}

/** DELETE 请求：直接返回 data 载荷 */
export function del<T = unknown>(url: string): Promise<T> {
  return request.delete(url).then((r: any) => r.data as T);
}

export default request;
