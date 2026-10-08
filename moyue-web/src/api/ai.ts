import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { PageQuery } from './types'

// ============ AI 任务 ============
export interface AiTaskVO {
  id: number
  taskType?: number
  prompt?: string
  model?: string
  status?: number
  result?: string
  costTokens?: number
  userId?: number
  createTime?: string
  remark?: string
}
export interface AiTaskQuery extends PageQuery {
  taskType?: number
  status?: number
  userId?: number
  prompt?: string
}
export function pageTasks(params: AiTaskQuery) {
  return request.get<PageResult<AiTaskVO>>('/admin/ai/tasks', { params })
}
export function createTask(data: Partial<AiTaskVO>) {
  return request.post<number>('/admin/ai/tasks', data)
}
export function updateTask(id: number, data: Partial<AiTaskVO>) {
  return request.put<boolean>(`/admin/ai/tasks/${id}`, data)
}
export function deleteTask(id: number) {
  return request.delete<boolean>(`/admin/ai/tasks/${id}`)
}
export function runTask(id: number) {
  return request.post<boolean>(`/admin/ai/tasks/${id}/run`)
}
export function taskSummary() {
  return request.get<Record<string, number>>('/admin/ai/tasks/summary')
}

// ============ AI 配额 ============
export interface AiQuotaVO {
  id: number
  userId?: number
  total?: number
  used?: number
  remain?: number
  createTime?: string
  remark?: string
}
export interface AiQuotaQuery extends PageQuery {
  userId?: number
}
export function pageQuotas(params: AiQuotaQuery) {
  return request.get<PageResult<AiQuotaVO>>('/admin/ai/quota', { params })
}
export function resetQuota(userId: number, total = 1000) {
  return request.post<boolean>('/admin/ai/quota/reset', null, { params: { userId, total } })
}
