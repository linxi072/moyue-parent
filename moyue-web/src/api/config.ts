import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { SysConfig, PageQuery } from './types'

export interface ConfigQuery extends PageQuery {
  configName?: string
  configKey?: string
  configType?: number
}

export function pageConfigs(params: ConfigQuery) {
  return request.get<PageResult<SysConfig>>('/admin/system/configs', { params })
}

export function createConfig(data: SysConfig) {
  return request.post<number>('/admin/system/configs', data)
}

export function updateConfig(id: number, data: SysConfig) {
  return request.put<boolean>(`/admin/system/configs/${id}`, data)
}

export function deleteConfig(id: number) {
  return request.delete<boolean>(`/admin/system/configs/${id}`)
}

export function getConfigValue(key: string) {
  return request.get<string>(`/admin/system/configs/key/${key}`)
}
