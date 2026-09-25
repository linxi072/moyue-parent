// 墨阅小说网前端 · axios 客户端封装
// 统一 baseURL=/api/v1（vite 开发代理将 /api 转发到 :8080 单体后端），
// 请求拦截注入身份头（单体无网关，前端直连 demo 自带 X-User-Id），
// 响应拦截解包 R<T>：成功返回 data，失败抛出 ApiError(code, message)。
import axios, { AxiosError, type AxiosInstance } from 'axios';
import type { ApiResponse } from '../types';
import { getAuthHeaders } from '../auth';

/** 业务成功码（对齐 ResultCode.SUCCESS） */
const SUCCESS = 0;

/** 统一业务异常 */
export class ApiError extends Error {
  code: number;
  constructor(code: number, message: string) {
    super(message);
    this.code = code;
    this.name = 'ApiError';
  }
}

const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
});

http.interceptors.request.use((config) => {
  const headers = getAuthHeaders();
  for (const [k, v] of Object.entries(headers)) {
    if (v != null) config.headers.set(k, v);
  }
  return config;
});

http.interceptors.response.use(
  (resp) => {
    const body = resp.data as ApiResponse<unknown>;
    if (body && body.code === SUCCESS) {
      return resp;
    }
    throw new ApiError(body?.code ?? -1, body?.message ?? '请求失败');
  },
  (err: AxiosError) => {
    const body = err.response?.data as ApiResponse<unknown> | undefined;
    if (body && typeof body.code === 'number') {
      return Promise.reject(new ApiError(body.code, body.message ?? '请求失败'));
    }
    return Promise.reject(new ApiError(-1, err.message || '网络错误'));
  }
);

/** GET 并解包 R<T>.data */
export async function apiGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const resp = await http.get<ApiResponse<T>>(url, { params });
  return (resp.data as ApiResponse<T>).data as T;
}

/** POST 并解包 R<T>.data */
export async function apiPost<T>(url: string, data?: unknown): Promise<T> {
  const resp = await http.post<ApiResponse<T>>(url, data);
  return (resp.data as ApiResponse<T>).data as T;
}

/** PUT 并解包 R<T>.data */
export async function apiPut<T>(url: string, data?: unknown): Promise<T> {
  const resp = await http.put<ApiResponse<T>>(url, data);
  return (resp.data as ApiResponse<T>).data as T;
}

/** DELETE 并解包 R<T>.data */
export async function apiDelete<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const resp = await http.delete<ApiResponse<T>>(url, { params });
  return (resp.data as ApiResponse<T>).data as T;
}

export default http;
