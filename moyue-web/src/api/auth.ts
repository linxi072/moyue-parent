import request from '@/utils/request'
import type { TokenVO, SysUser } from './types'

// 认证端点在 moyue-auth(8090)，网关与单体都暴露在 /api/v1/system 下
export function login(username: string, password: string) {
  return request.post<TokenVO>('/system/login', { username, password })
}

export function refresh(refreshToken: string) {
  return request.post<TokenVO>('/system/refresh', null, {
    headers: { 'X-Refresh-Token': refreshToken }
  })
}

/** 当前登录用户资料（含角色与权限） */
export function fetchProfile() {
  return request.get<SysUser & { roleKeys: string[]; permissions: string[] }>(
    '/admin/system/users/profile'
  )
}

export function updateProfile(data: Partial<SysUser>) {
  return request.put<boolean>('/admin/system/users/profile', data)
}

export function updatePassword(oldPassword: string, newPassword: string) {
  return request.put<boolean>('/admin/system/users/profile/password', {
    oldPassword,
    newPassword
  })
}
