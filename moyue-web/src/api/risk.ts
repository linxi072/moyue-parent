import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { PageQuery } from './types'

// ============ 审核工单 ============
export interface AuditRecordVO {
  id: number
  bizType?: number
  bizId?: string
  content?: string
  status?: number
  auditor?: string
  reason?: string
  createTime?: string
  remark?: string
}
export interface AuditRecordQuery extends PageQuery {
  bizType?: number
  status?: number
  bizId?: string
  content?: string
}
export function pageAudits(params: AuditRecordQuery) {
  return request.get<PageResult<AuditRecordVO>>('/admin/risk/audit-records', { params })
}
export function createAudit(data: Partial<AuditRecordVO>) {
  return request.post<number>('/admin/risk/audit-records', data)
}
export function updateAudit(id: number, data: Partial<AuditRecordVO>) {
  return request.put<boolean>(`/admin/risk/audit-records/${id}`, data)
}
export function deleteAudit(id: number) {
  return request.delete<boolean>(`/admin/risk/audit-records/${id}`)
}
export function approveAudit(id: number, auditor?: string) {
  return request.post<boolean>(`/admin/risk/audit-records/${id}/approve`, null, { params: { auditor } })
}
export function rejectAudit(id: number, auditor?: string, reason?: string) {
  return request.post<boolean>(`/admin/risk/audit-records/${id}/reject`, null, { params: { auditor, reason } })
}

// ============ 举报工单 ============
export interface ReportTicketVO {
  id: number
  bizType?: number
  bizId?: string
  reporterId?: number
  reason?: string
  content?: string
  status?: number
  handler?: string
  handleReason?: string
  createTime?: string
  remark?: string
}
export interface ReportTicketQuery extends PageQuery {
  bizType?: number
  status?: number
  bizId?: string
  reporterId?: number
}
export function pageReports(params: ReportTicketQuery) {
  return request.get<PageResult<ReportTicketVO>>('/admin/risk/reports', { params })
}
export function submitReport(data: Partial<ReportTicketVO>) {
  return request.post<number>('/admin/risk/reports', data)
}
export function handleReport(id: number, status: number, handler?: string, handleReason?: string) {
  return request.post<boolean>(`/admin/risk/reports/${id}/handle`, null, { params: { status, handler, handleReason } })
}

// ============ 敏感词 ============
export interface SensitiveWordVO {
  id: number
  word?: string
  level?: number
  enabled?: number
  hitCount?: number
  createTime?: string
  remark?: string
}
export interface SensitiveWordQuery extends PageQuery {
  word?: string
  level?: number
  enabled?: number
}
export function pageSensitiveWords(params: SensitiveWordQuery) {
  return request.get<PageResult<SensitiveWordVO>>('/admin/risk/sensitive-words', { params })
}
export function createSensitiveWord(data: Partial<SensitiveWordVO>) {
  return request.post<number>('/admin/risk/sensitive-words', data)
}
export function updateSensitiveWord(id: number, data: Partial<SensitiveWordVO>) {
  return request.put<boolean>(`/admin/risk/sensitive-words/${id}`, data)
}
export function deleteSensitiveWord(id: number) {
  return request.delete<boolean>(`/admin/risk/sensitive-words/${id}`)
}
export function enableSensitiveWord(id: number) {
  return request.post<boolean>(`/admin/risk/sensitive-words/${id}/enable`)
}
export function disableSensitiveWord(id: number) {
  return request.post<boolean>(`/admin/risk/sensitive-words/${id}/disable`)
}
export function sensitiveContains(text: string) {
  return request.get<boolean>('/admin/risk/sensitive-words/contains', { params: { text } })
}
export function sensitiveMatchLevel(text: string) {
  return request.get<number>('/admin/risk/sensitive-words/match-level', { params: { text } })
}
