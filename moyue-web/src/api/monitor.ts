import request from '@/utils/request'
import type { ServerVO, RedisInfoVO, DruidPoolVO, ServerInstance } from './types'

/** ⑫ 服务监控：5 个端点 */
export function serverInfo() {
  return request.get<ServerVO>('/admin/system/monitor/server')
}

export function jvmInfo() {
  return request.get<ServerVO['jvm']>('/admin/system/monitor/server/jvm')
}

export function diskInfo() {
  return request.get<ServerVO['disk']>('/admin/system/monitor/server/disk')
}

/** 服务实例清单（⑫ 多实例健康；单实例部署返回 local 实例） */
export function serverInstances() {
  return request.get<ServerInstance[]>('/admin/system/monitor/server/instances')
}

/** 指定实例的监控数据（server + health 详情） */
export function serverInstanceDetail(instanceId: string) {
  return request.get<Record<string, unknown>>(`/admin/system/monitor/server/instances/${instanceId}`)
}

/**
 * ⑬ 缓存监控 —— 契约见说明书 7.6。
 * 注意：说明书**没有**「清空整库」端点，只提供按前缀清理；
 * 整库清空对生产风险过高，前端同样不提供该入口。
 */
export function redisInfo() {
  return request.get<RedisInfoVO>('/admin/system/monitor/cache')
}

export interface RedisKeysResult {
  keys: string[]
  total: number
  truncated: boolean
}

/** 按前缀列举 key（前缀必填，单页上限 200，后端禁 KEYS *） */
export function redisKeyList(prefix: string, limit = 100) {
  return request.get<RedisKeysResult>('/admin/system/monitor/cache/keys', {
    params: { prefix, limit }
  })
}

export interface RedisValueResult {
  key: string
  type: string
  ttlSeconds: number
  value: string
  error?: string
}

export function redisKeyValue(key: string) {
  return request.get<RedisValueResult>('/admin/system/monitor/cache/value', { params: { key } })
}

export function deleteRedisKey(key: string) {
  return request.delete<boolean>('/admin/system/monitor/cache/keys', { params: { key } })
}

/** 按前缀批量清理（需二次确认，后端记录操作日志） */
export function deleteRedisByPrefix(prefix: string) {
  return request.delete<number>('/admin/system/monitor/cache/keys/prefix', { params: { prefix } })
}

/** 命令调用统计 Top N */
export function redisCommandStats() {
  return request.get<Record<string, string>>('/admin/system/monitor/cache/command-stats')
}

/** Key 数量与按 DB 分布 */
export function redisKeyspace() {
  return request.get<{ dbSize: Record<string, number>; keyspace: string }>(
    '/admin/system/monitor/cache/keyspace'
  )
}

/** ⑭ 连接池监视：6 个端点 */
export function poolList() {
  return request.get<DruidPoolVO[]>('/admin/system/monitor/pool')
}

/** 数据源清单（多数据源场景，与 poolList 同源但强调按数据源维度呈现） */
export function poolDatasources() {
  return request.get<DruidPoolVO[]>('/admin/system/monitor/pool/datasources')
}

export function sqlList() {
  return request.get<any[]>('/admin/system/monitor/pool/sql')
}

export function slowSqlList() {
  return request.get<any[]>('/admin/system/monitor/pool/sql/slow')
}

export function urlStats() {
  return request.get<any[]>('/admin/system/monitor/pool/url')
}

export function resetSqlStats() {
  return request.post<boolean>('/admin/system/monitor/pool/sql/reset')
}
