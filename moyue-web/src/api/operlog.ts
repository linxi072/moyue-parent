import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { SysOperLog, PageQuery } from './types'

export interface OperLogQuery extends PageQuery {
  title?: string
  operName?: string
  businessType?: number
  status?: number
}

/** 统计端点实际返回字段（SysOperLogServiceImpl#stats） */
export interface OperLogStats {
  total?: number
  failTotal?: number
  byBusinessType?: Record<string, number>
}

// 路径以架构说明书 7.6 ⑦ 为准：/admin/system/operlogs
export function pageOperLogs(params: OperLogQuery) {
  return request.get<PageResult<SysOperLog>>('/admin/system/operlogs', { params })
}

export function getOperLog(id: number) {
  return request.get<SysOperLog>(`/admin/system/operlogs/${id}`)
}

export function deleteOperLog(id: number) {
  return request.delete<boolean>(`/admin/system/operlogs/${id}`)
}

export function deleteOperLogs(ids: number[]) {
  return request.delete<boolean>('/admin/system/operlogs', { data: ids })
}

export function clearOperLogs() {
  return request.delete<number>('/admin/system/operlogs/clear')
}

export function exportOperLogs(params: OperLogQuery) {
  return request.get('/admin/system/operlogs/export', { params, responseType: 'blob' })
}

/** 统计概览（列表页顶部卡片） */
export function operLogStats() {
  return request.get<OperLogStats>('/admin/system/operlogs/stats')
}
