import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { SysUserOnline, PageQuery } from './types'

export interface OnlineQuery extends PageQuery {
  username?: string
  ip?: string
}

export function pageOnlineUsers(params: OnlineQuery) {
  return request.get<PageResult<SysUserOnline>>('/admin/system/online', { params })
}

export function forceLogout(tokenId: string) {
  return request.delete<boolean>(`/admin/system/online/${tokenId}`)
}

/**
 * 批量强退。
 * 说明书 ⑨ 定义为 {@code DELETE /admin/system/online}（请求体传 tokenIds），
 * 与「删除单条」路径区分在有无 tokenId，而非另开 /batch。
 */
export function batchForceLogout(tokenIds: string[]) {
  return request.delete<number>('/admin/system/online', { data: tokenIds })
}

export function onlineCount() {
  return request.get<number>('/admin/system/online/count')
}
