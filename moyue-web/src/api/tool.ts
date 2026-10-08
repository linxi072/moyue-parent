import request from '@/utils/request'

/**
 * 系统接口（⑪）后端契约。
 *
 * <p>数据源为 springdoc-openapi 生成的 OpenAPI 契约，由后台 SysApiController
 * （/api/v1/admin/system/apis）统一出口，前端不再直连原始 OpenAPI JSON 地址。
 * request 已解包 R.data，故各方法直接返回业务类型。
 */

/** 接口分组（Tag）及其接口数量 */
export interface ApiGroup {
  tag: string
  count: number
}

/** 某分组下的单个接口 */
export interface ApiItem {
  path: string
  method: string
  summary: string
  /** 是否走后台鉴权（路径以 /api/v1/admin 开头） */
  auth: boolean
}

/** 接口统计概览 */
export interface ApiStats {
  total: number
  authCount: number
  /** 鉴权接口占比（0~100） */
  authRatio: number
  byTag: Record<string, number>
}

/** 接口详情（pathItem 为 OpenAPI PathItem 对象） */
export interface ApiDetail {
  apiId: string
  found: boolean
  pathItem?: Record<string, unknown>
}

/** 分组列表（含各组接口数量） */
export function listApiGroups() {
  return request.get<ApiGroup[]>('/admin/system/apis')
}

/** 某分组下的接口清单（tag 含中文，需编码后放入路径） */
export function listApisByTag(tag: string) {
  return request.get<ApiItem[]>('/admin/system/apis/' + encodeURIComponent(tag))
}

/** 接口详情；apiId 为完整接口路径（含斜杠），走请求参数 */
export function getApiDetail(apiId: string) {
  return request.get<ApiDetail>('/admin/system/apis/detail', { params: { apiId } })
}

/** 接口统计（总数 / 按 Tag 分布 / 鉴权占比） */
export function getApiStats() {
  return request.get<ApiStats>('/admin/system/apis/stats')
}

/** 原始 OpenAPI 契约（统一响应体内部承载，已解包） */
export function getRawOpenApi() {
  return request.get<Record<string, unknown>>('/admin/system/apis/openapi')
}

/** 与基线契约的差集（基线未配置时返回提示） */
export function getApiDiff() {
  return request.get<Record<string, unknown>>('/admin/system/apis/diff')
}
