import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { GenTable, GenTableColumn, PageQuery } from './types'

export interface GenTableQuery extends PageQuery {
  tableName?: string
  tableComment?: string
}

/** ⑮ 代码生成：10 个端点 */
export function dbTables(tableName?: string) {
  return request.get<any[]>('/admin/system/generator/tables', { params: { tableName } })
}

export function importTables(tableNames: string[]) {
  return request.post<number>('/admin/system/generator/import', tableNames)
}

export function pageGenConfigs(params: GenTableQuery) {
  return request.get<PageResult<GenTable>>('/admin/system/generator/configs', { params })
}

export function genConfigDetail(tableId: number) {
  return request.get<{ table: GenTable; columns: GenTableColumn[] }>(
    `/admin/system/generator/configs/${tableId}`
  )
}

export function updateGenConfig(data: GenTable & { columns?: GenTableColumn[] }) {
  return request.put<boolean>('/admin/system/generator/configs', data)
}

export function deleteGenConfig(tableId: number) {
  return request.delete<boolean>(`/admin/system/generator/configs/${tableId}`)
}

export function syncGenTable(tableId: number) {
  return request.post<number>(`/admin/system/generator/sync/${tableId}`)
}

export function previewGenCode(tableId: number) {
  return request.get<Record<string, string>>(`/admin/system/generator/preview/${tableId}`)
}

/** 打包下载 ZIP（响应是文件流，交给 downloadFile 处理） */
export function downloadGenCodeUrl(tableId: number) {
  return `/admin/system/generator/download/${tableId}`
}

/** 写入服务器磁盘（受环境限制时后端返回 403） */
export function generateToPath(tableId: number, genPath?: string) {
  return request.post<string[]>(`/admin/system/generator/generate/${tableId}`, null, {
    params: { genPath }
  })
}
