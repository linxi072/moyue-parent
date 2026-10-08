import request from '@/utils/request'
import type { SysMenu, PageQuery } from './types'

export interface MenuQuery extends PageQuery {
  menuName?: string
  status?: number
}

export function listMenus(params: MenuQuery) {
  return request.get<SysMenu[]>('/admin/system/menus', { params })
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
