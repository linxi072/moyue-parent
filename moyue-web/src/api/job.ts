import request from '@/utils/request'
import type { JobInfo, JobLog, PageQuery } from './types'

export interface JobQuery extends PageQuery {
  jobDesc?: string
  jobGroup?: string
  triggerStatus?: number
}

/**
 * 定时任务复用 XXL-Job Admin（架构 ADR-13），后端是 OpenAPI 代理。
 *
 * <p>关键约束（说明书 ⑩）：<strong>不提供新增 / 编辑 / 删除任务</strong> ——
 * 任务由开发在代码中声明 @XxlJob 并由 Admin 注册，避免前端配置的 Cron 与代码不同步。
 * 因此本模块只有「列表 / 详情 / 启停 / 触发 / 日志」。
 *
 * <p>后端直接透传 Admin 的 JsonNode，这里做一层结构归一，页面不必感知 XXL-Job 的字段命名。
 */
export interface XxlPage<T> {
  records: T[]
  total: number
}

/** Admin 分页返回结构（records / recordsFiltered / recordsTotal 混用，这里都兜住） */
interface XxlRawPage {
  data?: any[]
  records?: any[]
  recordsFiltered?: number
  recordsTotal?: number
}

function normalize<T>(raw: XxlRawPage | null): XxlPage<T> {
  const rows = (raw?.data ?? raw?.records ?? []) as T[]
  const total = raw?.recordsTotal ?? raw?.recordsFiltered ?? rows.length
  return { records: rows, total }
}

export async function pageJobs(params: JobQuery) {
  const raw = await request.get<XxlRawPage>('/admin/system/jobs', {
    params: { start: (params.page - 1) * params.size, length: params.size, jobDesc: params.jobDesc, jobGroup: params.jobGroup }
  })
  return normalize<JobInfo>(raw)
}

export async function getJob(id: number) {
  const raw = await request.get<XxlRawPage>(`/admin/system/jobs/${id}`)
  return normalize<JobInfo>(raw).records[0] as JobInfo | undefined
}

/** 启停任务：status = 1 启动 / 0 停止 */
export function changeJobStatus(id: number, status: 0 | 1) {
  return request.put<unknown>(`/admin/system/jobs/${id}/status`, { status: String(status) })
}

/** 手动触发一次，可传执行参数 */
export function triggerJob(id: number, executorParam = '') {
  return request.post<unknown>(`/admin/system/jobs/${id}/trigger`, { executorParam })
}

export async function pageJobLogs(jobId: number, params: PageQuery) {
  const raw = await request.get<XxlRawPage>(`/admin/system/jobs/${jobId}/logs`, {
    params: { start: (params.page - 1) * params.size, length: params.size }
  })
  return normalize<JobLog>(raw)
}

/**
 * 调度中心连通性。
 * Admin 不可达时整个模块降级为只读 —— 前端据此隐藏启停 / 触发按钮。
 */
export function jobHealth() {
  return request.get<{ available: boolean; address: string }>('/admin/system/jobs/health')
}
