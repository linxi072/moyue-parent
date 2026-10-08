import request from '@/utils/request'
import type { SysDept, PageQuery } from './types'

export interface DeptQuery extends PageQuery {
  deptName?: string
  status?: number
}

export function listDepts(params: DeptQuery) {
  return request.get<SysDept[]>('/admin/system/depts', { params })
}

export function getDept(id: number) {
  return request.get<SysDept>(`/admin/system/depts/${id}`)
}

export function createDept(data: SysDept) {
  return request.post<number>('/admin/system/depts', data)
}

export function updateDept(id: number, data: SysDept) {
  return request.put<boolean>(`/admin/system/depts/${id}`, data)
}

export function deleteDept(id: number) {
  return request.delete<boolean>(`/admin/system/depts/${id}`)
}
