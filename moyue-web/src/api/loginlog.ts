import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { SysLoginLog, PageQuery } from './types'

export interface LoginLogQuery extends PageQuery {
  username?: string
  ip?: string
  status?: number
}

// 路径以架构说明书 7.6 ⑧ 为准：/admin/system/loginlogs
export function pageLoginLogs(params: LoginLogQuery) {
  return request.get<PageResult<SysLoginLog>>('/admin/system/loginlogs', { params })
}

export function getLoginLog(id: number) {
  return request.get<SysLoginLog>(`/admin/system/loginlogs/${id}`)
}

export function deleteLoginLog(id: number) {
  return request.delete<boolean>(`/admin/system/loginlogs/${id}`)
}

export function clearLoginLogs() {
  return request.delete<boolean>('/admin/system/loginlogs/clear')
}

/**
 * 解锁账号（清理登录失败计数）。
 * 说明书定义为 GET —— 语义上是「状态变更用 GET」的少数例外，按契约实现。
 */
export function unlockAccount(username: string) {
  return request.get<boolean>(`/admin/system/loginlogs/unlock/${username}`)
}

/** 按账号查登录记录（含失败次数，供风控查看） */
export function accountLoginLogs(username: string, params: PageQuery) {
  return request.get<PageResult<SysLoginLog>>(`/admin/system/loginlogs/account/${username}`, {
    params
  })
}

export function exportLoginLogs(params: LoginLogQuery) {
  return request.get('/admin/system/loginlogs/export', { params, responseType: 'blob' })
}
