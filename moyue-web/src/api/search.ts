import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { PageQuery } from './types'

export interface SearchHotWordVO {
  id: number
  word?: string
  hitCount?: number
  weight?: number
  enabled?: number
  createTime?: string
  remark?: string
}

export interface BlockWordVO {
  id: number
  word?: string
  level?: number
  enabled?: number
  hitCount?: number
  createTime?: string
  remark?: string
}

export interface SearchHotWordQuery extends PageQuery {
  word?: string
  enabled?: number
}

export interface BlockWordQuery extends PageQuery {
  word?: string
  level?: number
  enabled?: number
}

// 热词
export function pageHotWords(params: SearchHotWordQuery) {
  return request.get<PageResult<SearchHotWordVO>>('/admin/search/hot-words', { params })
}
export function createHotWord(data: Partial<SearchHotWordVO>) {
  return request.post<number>('/admin/search/hot-words', data)
}
export function updateHotWord(id: number, data: Partial<SearchHotWordVO>) {
  return request.put<boolean>(`/admin/search/hot-words/${id}`, data)
}
export function deleteHotWord(id: number) {
  return request.delete<boolean>(`/admin/search/hot-words/${id}`)
}
export function topHotWords(limit = 10) {
  return request.get<SearchHotWordVO[]>('/admin/search/hot-words/top', { params: { limit } })
}
export function suggestHotWords(keyword: string) {
  return request.get<SearchHotWordVO[]>('/admin/search/hot-words/suggest', { params: { keyword } })
}

// 屏蔽词
export function pageBlockWords(params: BlockWordQuery) {
  return request.get<PageResult<BlockWordVO>>('/admin/search/block-words', { params })
}
export function createBlockWord(data: Partial<BlockWordVO>) {
  return request.post<number>('/admin/search/block-words', data)
}
export function updateBlockWord(id: number, data: Partial<BlockWordVO>) {
  return request.put<boolean>(`/admin/search/block-words/${id}`, data)
}
export function deleteBlockWord(id: number) {
  return request.delete<boolean>(`/admin/search/block-words/${id}`)
}
export function enableBlockWord(id: number) {
  return request.post<boolean>(`/admin/search/block-words/${id}/enable`)
}
export function disableBlockWord(id: number) {
  return request.post<boolean>(`/admin/search/block-words/${id}/disable`)
}
