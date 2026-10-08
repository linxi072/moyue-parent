import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { SysRole, SysMenu, PageQuery } from './types'

export interface RoleQuery extends PageQuery {
  roleName?: string
  roleKey?: string
  status?: number
}

export function pageRoles(params: RoleQuery) {
  return request.get<PageResult<SysRole>>('/admin/system/roles', { params })
}

export function getRole(id: number) {
  return request.get<SysRole>(`/admin/system/roles/${id}`)
}

export function createRole(data: SysRole) {
  return request.post<number>('/admin/system/roles', data)
}

export function updateRole(id: number, data: SysRole) {
  return request.put<boolean>(`/admin/system/roles/${id}`, data)
}

export function deleteRole(id: number) {
  return request.delete<boolean>(`/admin/system/roles/${id}`)
}

/**
 * 说明书 ② 未定义角色启停端点，角色状态变更走 {@link updateRole} 整体编辑。
 * 这样既能改 status，也避免为单一字段新增非契约端点。
 */
export function changeRoleStatus(id: number, status: number, base: SysRole) {
  return updateRole(id, { ...base, status })
}

export function assignRoleMenus(id: number, menuIds: number[]) {
  return request.put<boolean>(`/admin/system/roles/${id}/menus`, menuIds)
}

/** 角色已授权菜单（弹窗回填用） */
export function getRoleMenus(id: number) {
  return request.get<number[]>(`/admin/system/roles/${id}/menus`)
}

/**
 * 角色数据权限部门（【架构缺口 G-10】）。
 * 说明书 ② 只有菜单授权端点，但 data_scope = 2（自定义数据）必须能圈定部门，
 * 否则该枚举形同虚设，故后端补齐并在此对接。
 */
export function getRoleDepts(id: number) {
  return request.get<number[]>(`/admin/system/roles/${id}/depts`)
}

export function assignRoleDepts(id: number, deptIds: number[]) {
  return request.put<boolean>(`/admin/system/roles/${id}/depts`, deptIds)
}

/** 全部菜单（角色授权弹窗用；说明书 ③ 的全量列表接口，前端自行组装成树） */
export function listAllMenus() {
  return request.get<SysMenu[]>('/admin/system/menus', { params: { page: 1, size: 500 } })
}
