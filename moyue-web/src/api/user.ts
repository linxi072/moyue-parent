import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { SysUser, PageQuery } from './types'

export interface UserQuery extends PageQuery {
  keyword?: string
  deptId?: number
  userType?: number
  status?: number
}

export function pageUsers(params: UserQuery) {
  return request.get<PageResult<SysUser>>('/admin/system/users', { params })
}

export function getUser(id: number) {
  return request.get<SysUser>(`/admin/system/users/${id}`)
}

export function createUser(data: SysUser) {
  return request.post<number>('/admin/system/users', data)
}

export function updateUser(id: number, data: SysUser) {
  return request.put<boolean>(`/admin/system/users/${id}`, data)
}

export function deleteUser(id: number) {
  return request.delete<boolean>(`/admin/system/users/${id}`)
}

export function changeUserStatus(id: number, status: number) {
  return request.put<boolean>(`/admin/system/users/${id}/status`, null, { params: { status } })
}

export function resetUserPassword(id: number, password: string) {
  return request.put<boolean>(`/admin/system/users/${id}/reset-pwd`, null, {
    params: { password }
  })
}

export function getUserRoles(id: number) {
  return request.get<number[]>(`/admin/system/users/${id}/roles`)
}

export function assignUserRoles(id: number, roleIds: number[]) {
  return request.put<boolean>(`/admin/system/users/${id}/roles`, roleIds)
}
