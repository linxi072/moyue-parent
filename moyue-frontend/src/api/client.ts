// 墨阅小说网前端 · axios 客户端封装
// 统一 baseURL=/api/v1（vite 开发代理将 /api 转发到 :8080 单体后端），
// 请求拦截注入身份头（单体无网关，前端直连 demo 自带 X-User-Id），
// 响应拦截解包 R<T>：成功返回 data，失败抛出 ApiError(code, message)。
// P0-#3：捕获 10002（未登录/令牌过期）与 HTTP 401 → 单飞刷新 accessToken → 重放原请求；
//        刷新失败则清空会话并跳转 #/login。
import axios, { AxiosError, type AxiosInstance, type AxiosResponse, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import type { ApiResponse, LoginVO } from '../types';
import { getAuthHeaders, getRefreshToken, saveSession, clearSession } from '../auth';

/** 业务成功码（对齐 ResultCode.SUCCESS） */
const SUCCESS = 0;
/** 未登录 / 令牌失效（对齐 ResultCode.UNAUTHORIZED） */
const UNAUTHORIZED = 10002;

/** 统一业务异常 */
export class ApiError extends Error {
  code: number;
  constructor(code: number, message: string) {
    super(message);
    this.code = code;
    this.name = 'ApiError';
  }
}

/** 标记刷新请求本身，避免刷新失败回调里再次触发刷新（死循环） */
const NO_REFRESH = Symbol('noAuthRefresh');

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

/** 单飞刷新：同一时刻只有一个 /auth/refresh 在途，其余并发请求共享其结果 */
let refreshFlight: Promise<string> | null = null;

function refreshTokenOnce(): Promise<string> {
  if (refreshFlight) return refreshFlight;
  const rt = getRefreshToken();
  if (!rt) {
    return Promise.reject(new ApiError(UNAUTHORIZED, '无刷新令牌'));
  }
  refreshFlight = (async () => {
    const vo = await rawRefresh(rt); // 不经过刷新分支的裸请求
    saveSession(vo);
    return vo.accessToken;
  })().finally(() => {
    refreshFlight = null;
  });
  return refreshFlight;
}

/** 裸刷新请求：跳过响应拦截器的刷新逻辑（自身 401/10002 直接抛错） */
async function rawRefresh(refreshToken: string): Promise<LoginVO> {
  const cfg = { [NO_REFRESH]: true } as unknown as AxiosRequestConfig;
  const resp = await http.post<ApiResponse<LoginVO>>('/auth/refresh', { refreshToken }, cfg);
  return (resp.data as ApiResponse<LoginVO>).data as LoginVO;
}

/** 会话过期处理：清空本地会话并跳转登录页（幂等，多次调用无害） */
function onSessionExpired(): void {
  clearSession();
  if (location.hash !== '#/login') location.hash = '#/login';
}

/** 命中未授权：尝试刷新并重放原请求；刷新失败则跳登录 */
function handleUnauthorized(config: InternalAxiosRequestConfig | undefined): Promise<unknown> {
  if (!config || (config as unknown as Record<symbol, boolean>)[NO_REFRESH]) {
    // 刷新接口自身失败 → 直接过期
    onSessionExpired();
    return Promise.reject(new ApiError(UNAUTHORIZED, '登录已过期，请重新登录'));
  }
  return refreshTokenOnce()
    .then(() => http(config)) // 重发原请求，请求拦截器会注入新 token
    .catch((e) => {
      onSessionExpired();
      return Promise.reject(e);
    });
}

http.interceptors.response.use(
  (resp) => {
    const body = resp.data as ApiResponse<unknown>;
    if (body && body.code === SUCCESS) {
      return resp;
    }
    if (body && body.code === UNAUTHORIZED) {
      return handleUnauthorized(resp.config) as unknown as AxiosResponse;
    }
    throw new ApiError(body?.code ?? -1, body?.message ?? '请求失败');
  },
  (err: AxiosError) => {
    const body = err.response?.data as ApiResponse<unknown> | undefined;
    if (body && typeof body.code === 'number') {
      if (body.code === UNAUTHORIZED) {
        return handleUnauthorized(err.config) as unknown as Promise<never>;
      }
      return Promise.reject(new ApiError(body.code, body.message ?? '请求失败'));
    }
    if (err.response?.status === 401) {
      return handleUnauthorized(err.config) as unknown as Promise<never>;
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
