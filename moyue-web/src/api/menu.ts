import request from '@/utils/request'
import type { SysMenu, PageQuery } from './types'

export interface MenuQuery extends PageQuery {
  menuName?: string
  status?: number
}

export function listMenus(params: MenuQuery) {
  return request.get<SysMenu[]>('/admin/system/menus', { params })
}

/** 当前登录者可见菜单树（后端按角色/RBAC 动态下发，动态菜单数据源） */
export function getCurrentMenus() {
  return request.get<SysMenu[]>('/admin/system/menus/current')
}

export function getMenu(id: number) {
  return request.get<SysMenu>(`/admin/system/menus/${id}`)
}

export function createMenu(data: SysMenu) {
  return request.post<number>('/admin/system/menus', data)
}

export function updateMenu(id: number, data: SysMenu) {
  return request.put<boolean>(`/admin/system/menus/${id}`, data)
}

export function deleteMenu(id: number) {
  return request.delete<boolean>(`/admin/system/menus/${id}`)
}
