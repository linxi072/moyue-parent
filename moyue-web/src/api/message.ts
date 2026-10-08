import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { PageQuery } from './types'

export interface MessageVO {
  id: number
  fromUser?: number
  toUser?: number
  title?: string
  content?: string
  type?: number
  readFlag?: number
  createTime?: string
  remark?: string
}

export interface MessageTemplateVO {
  id: number
  code?: string
  title?: string
  content?: string
  type?: number
  enabled?: number
  createTime?: string
  remark?: string
}

export interface MessageQuery extends PageQuery {
  toUser?: number
  type?: number
  readFlag?: number
  title?: string
}

// 站内信
export function pageMessages(params: MessageQuery) {
  return request.get<PageResult<MessageVO>>('/admin/message/messages', { params })
}
export function createMessage(data: Partial<MessageVO>) {
  return request.post<number>('/admin/message/messages', data)
}
export function updateMessage(id: number, data: Partial<MessageVO>) {
  return request.put<boolean>(`/admin/message/messages/${id}`, data)
}
export function deleteMessage(id: number) {
  return request.delete<boolean>(`/admin/message/messages/${id}`)
}
export function unreadCount(toUser: number) {
  return request.get<number>('/admin/message/messages/unread-count', { params: { toUser } })
}
export function sendMessage(toUser: number, title: string, content: string, type = 1, templateCode?: string, name?: string) {
  return request.post<number>('/admin/message/messages/send', null, { params: { toUser, title, content, type, templateCode, name } })
}
export function sendBatch(toUserIds: number[], title: string, content: string, type = 1, templateCode?: string, name?: string) {
  const sp = new URLSearchParams()
  toUserIds.forEach((id) => sp.append('toUserIds', String(id)))
  sp.append('title', title)
  sp.append('content', content)
  sp.append('type', String(type))
  if (templateCode) sp.append('templateCode', templateCode)
  if (name) sp.append('name', name)
  return request.post<number[]>(`/admin/message/messages/send-batch?${sp.toString()}`, null)
}
export function readAll(ids: number[]) {
  return request.post<boolean>('/admin/message/messages/read-all', ids)
}

// 模板
export function pageTemplates(params: PageQuery & { keyword?: string; type?: number; enabled?: number }) {
  return request.get<PageResult<MessageTemplateVO>>('/admin/message/templates', { params })
}
export function createTemplate(data: Partial<MessageTemplateVO>) {
  return request.post<number>('/admin/message/templates', data)
}
export function updateTemplate(id: number, data: Partial<MessageTemplateVO>) {
  return request.put<boolean>(`/admin/message/templates/${id}`, data)
}
export function deleteTemplate(id: number) {
  return request.delete<boolean>(`/admin/message/templates/${id}`)
}
export function enableTemplate(id: number) {
  return request.post<boolean>(`/admin/message/templates/${id}/enable`)
}
export function disableTemplate(id: number) {
  return request.post<boolean>(`/admin/message/templates/${id}/disable`)
}
