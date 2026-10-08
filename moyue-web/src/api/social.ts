import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { PageQuery } from './types'

export interface CommentVO {
  id: number
  bookId: number
  chapterId?: number
  userId: number
  replyTo?: number
  content: string
  likeCount?: number
  status?: number
  top?: number
  createTime?: string
}

export interface ImConversationVO {
  id: number
  type: number
  title?: string
  ownerId?: number
  lastMessage?: string
  lastMessageTime?: string
  memberIds?: number[]
  unreadCount?: number
  disabled?: number
}

export interface ImMessageVO {
  id: number
  conversationId: number
  senderId: number
  content: string
  type?: number
  status?: number
  createTime?: string
}

export interface CommentQuery extends PageQuery {
  bookId?: number
  chapterId?: number
  userId?: number
  status?: number
  top?: number
}

// ============ C 端（读者互动） ============
export function pageComments(params: CommentQuery) {
  return request.get<PageResult<CommentVO>>('/comments', { params })
}

export function deleteComment(id: number) {
  return request.delete<boolean>(`/comments/${id}`)
}

export function toggleLike(id: number) {
  return request.post<number>(`/comments/${id}/like`)
}

export function listConversations() {
  return request.get<ImConversationVO[]>('/im/conversations')
}

export function getConversation(id: number) {
  return request.get<ImConversationVO>(`/im/conversations/${id}`)
}

export function listMessages(conversationId: number, params: { cursor?: number; size?: number }) {
  return request.get<ImMessageVO[]>(`/im/conversations/${conversationId}/messages`, { params })
}

export function sendMessage(conversationId: number, content: string, type = 1) {
  return request.post<ImMessageVO>(`/im/conversations/${conversationId}/messages`, { content, type })
}

export function recallMessage(conversationId: number, messageId: number) {
  return request.post<boolean>(`/im/conversations/${conversationId}/messages/${messageId}/recall`)
}

// ============ 运营端（管理后台） ============
export function pageAdminComments(params: CommentQuery) {
  return request.get<PageResult<CommentVO>>('/admin/social/comments', { params })
}

export function deleteAdminComment(id: number) {
  return request.delete<boolean>(`/admin/social/comments/${id}`)
}

export function topAdminComment(id: number, top: number) {
  return request.post<boolean>(`/admin/social/comments/${id}/top`, null, { params: { top } })
}

export function auditAdminComment(id: number, status: number) {
  return request.post<boolean>(`/admin/social/comments/${id}/audit`, null, { params: { status } })
}

export function listAdminConversations(params: PageQuery) {
  return request.get<PageResult<ImConversationVO>>('/admin/social/im/conversations', { params })
}

export function listAdminMessages(conversationId: number, size = 20) {
  return request.get<ImMessageVO[]>(`/admin/social/im/conversations/${conversationId}/messages`, { params: { size } })
}

export function disableConversation(id: number, disabled: number) {
  return request.post<boolean>(`/admin/social/im/conversations/${id}/disable`, null, { params: { disabled } })
}
