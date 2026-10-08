import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { SysDictType, SysDictData, PageQuery } from './types'

export interface DictTypeQuery extends PageQuery {
  dictName?: string
  dictType?: string
  status?: number
}

export function pageDictTypes(params: DictTypeQuery) {
  return request.get<PageResult<SysDictType>>('/admin/system/dict/types', { params })
}

export function createDictType(data: SysDictType) {
  return request.post<number>('/admin/system/dict/types', data)
}

export function updateDictType(id: number, data: SysDictType) {
  return request.put<boolean>(`/admin/system/dict/types/${id}`, data)
}

export function deleteDictType(id: number) {
  return request.delete<boolean>(`/admin/system/dict/types/${id}`)
}

export function pageDictData(typeId: number, params: PageQuery) {
  return request.get<PageResult<SysDictData>>(`/admin/system/dict/types/${typeId}/data`, { params })
}

export function createDictData(data: SysDictData) {
  return request.post<number>('/admin/system/dict/data', data)
}

export function updateDictData(id: number, data: SysDictData) {
  return request.put<boolean>(`/admin/system/dict/data/${id}`, data)
}

export function deleteDictData(id: number) {
  return request.delete<boolean>(`/admin/system/dict/data/${id}`)
}

/** 按类型取字典项（下拉选项用） */
export function listDictData(dictType: string) {
  return request.get<SysDictData[]>(`/admin/system/dict/data/type/${dictType}`)
}
