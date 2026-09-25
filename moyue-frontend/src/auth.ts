// 墨阅小说网前端 · 鉴权态管理
// 单体 JWT（JwtProvider）payload 含 userId / role claim，登录后前端解码 payload 取身份，
// 经 localStorage 持久化，并为请求注入 X-User-Id / X-User-Role / Authorization 头。
import type { LoginVO } from './types';

const TOKEN_KEY = 'moyue_token';
const REFRESH_KEY = 'moyue_refresh';
const UID_KEY = 'moyue_uid';
const ROLE_KEY = 'moyue_role';

export interface Session {
  accessToken: string;
  userId: number;
  role: number | null;
}

/** 解析 JWT payload（base64url 中段），提取 userId / role */
function decodeJwt(token: string): { userId: number; role: number | null } {
  const parts = token.split('.');
  if (parts.length < 2) throw new Error('非法 token');
  const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
  const bin = atob(b64);
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
  const json = JSON.parse(new TextDecoder().decode(bytes)) as { userId?: number; role?: number };
  return {
    userId: Number(json.userId),
    role: json.role == null ? null : Number(json.role),
  };
}

/** 登录成功后保存会话 */
export function saveSession(vo: LoginVO): Session {
  const { userId, role } = decodeJwt(vo.accessToken);
  localStorage.setItem(TOKEN_KEY, vo.accessToken);
  localStorage.setItem(REFRESH_KEY, vo.refreshToken);
  localStorage.setItem(UID_KEY, String(userId));
  if (role != null) localStorage.setItem(ROLE_KEY, String(role));
  return { accessToken: vo.accessToken, userId, role };
}

/** 读取当前会话；未登录返回 null */
export function getSession(): Session | null {
  const token = localStorage.getItem(TOKEN_KEY);
  const uid = localStorage.getItem(UID_KEY);
  if (!token || !uid) return null;
  const roleRaw = localStorage.getItem(ROLE_KEY);
  return {
    accessToken: token,
    userId: Number(uid),
    role: roleRaw == null ? null : Number(roleRaw),
  };
}

/** 退出登录，清空本地会话 */
export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(UID_KEY);
  localStorage.removeItem(ROLE_KEY);
}

export function isLoggedIn(): boolean {
  return getSession() != null;
}

/** 组装请求身份头（单体无网关，前端直连 demo 自带） */
export function getAuthHeaders(): Record<string, string> {
  const s = getSession();
  const h: Record<string, string> = {};
  if (s) {
    h['X-User-Id'] = String(s.userId);
    if (s.role != null) h['X-User-Role'] = String(s.role);
    h['Authorization'] = `Bearer ${s.accessToken}`;
  }
  return h;
}
