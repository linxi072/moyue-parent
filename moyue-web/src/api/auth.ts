import { post, get } from '@/utils/request';

export interface LoginReq {
  phone: string;
  password: string;
}

export interface RegisterReq {
  phone: string;
  code: string;
  password: string;
}

export interface LoginVO {
  accessToken: string;
  refreshToken: string;
}

export interface UserInfoVO {
  id: number;
  phone: string;
  nickname: string;
  role: number;
  status: number;
}

/** 手机号 + 密码登录 */
export function login(req: LoginReq) {
  return post<LoginVO>('/auth/login', req);
}

/** 注册（演示版不校验短信验证码） */
export function register(req: RegisterReq) {
  return post<void>('/auth/register', req);
}

/** 用 refreshToken 换取新的 accessToken */
export function refresh(refreshToken: string) {
  return post<LoginVO>('/auth/refresh', { refreshToken });
}

/** 获取当前登录用户资料 */
export function me() {
  return get<UserInfoVO>('/users/me');
}
