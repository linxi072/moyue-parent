import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { PageQuery } from './types'

export interface BookVO {
  id: number
  title: string
  authorName?: string
  categoryId?: number
  status?: number
  wordCount?: number
  intro?: string
  coverUrl?: string
  createTime?: string
  remark?: string
}

export interface ChapterVO {
  id: number
  bookId: number
  chapterNo: number
  title: string
  content?: string
  wordCount?: number
  status?: number
  publishTime?: string
  createTime?: string
  updateTime?: string
}

export interface BookShelfVO {
  id: number
  /** 书架所属用户，仅管理端全局查询返回 */
  userId?: number
  bookId: number
  bookTitle?: string
  coverUrl?: string
  lastChapterNo?: number
  createTime?: string
}

export interface ChapterQuery extends PageQuery {
  bookId?: number
  status?: number
  title?: string
}

/** 运营端作品分页查询条件：分页 + 标题模糊 + 状态（0连载中/1已完结/2已下架） */
export interface BookQuery extends PageQuery {
  title?: string
  status?: number
}

export function pageAdminBooks(params: BookQuery) {
  return request.get<PageResult<BookVO>>('/admin/content/books', { params })
}

export function pageChapters(params: ChapterQuery) {
  return request.get<PageResult<ChapterVO>>('/chapters', { params })
}

export function getChapter(id: number) {
  return request.get<ChapterVO>(`/chapters/${id}`)
}

export function createChapter(data: Partial<ChapterVO>) {
  return request.post<number>('/chapters', data)
}

export function updateChapter(id: number, data: Partial<ChapterVO>) {
  return request.put<boolean>(`/chapters/${id}`, data)
}

export function deleteChapter(id: number) {
  return request.delete<boolean>(`/chapters/${id}`)
}

export function publishChapter(id: number, publishTime?: string) {
  return request.post<ChapterVO>(`/chapters/${id}/publish`, null, { params: { publishTime } })
}

export function reorderChapter(id: number, no: number) {
  return request.put<boolean>(`/chapters/${id}/order`, null, { params: { no } })
}

export function drafts(bookId: number) {
  return request.get<ChapterVO[]>('/chapters/drafts', { params: { bookId } })
}

export function listShelf(params: PageQuery & { bookTitle?: string }) {
  return request.get<PageResult<BookShelfVO>>('/read/bookshelf', { params })
}

// ============ 运营端（管理端） ============
export function pageAdminChapters(params: ChapterQuery) {
  return request.get<PageResult<ChapterVO>>('/admin/content/chapters', { params })
}
export function publishAdminChapter(id: number) {
  return request.post<ChapterVO>(`/admin/content/chapters/${id}/publish`)
}
export function offshelfAdminChapter(id: number) {
  return request.post<boolean>(`/admin/content/chapters/${id}/offshelf`)
}
export function reorderAdminChapter(id: number, no: number) {
  return request.post<boolean>(`/admin/content/chapters/${id}/reorder`, null, { params: { targetNo: no } })
}
export function deleteAdminChapter(id: number) {
  return request.delete<boolean>(`/admin/content/chapters/${id}`)
}
export function onlineBook(id: number) {
  return request.post<boolean>(`/admin/content/books/${id}/online`)
}
export function offlineBook(id: number) {
  return request.post<boolean>(`/admin/content/books/${id}/offline`)
}
export function pageAdminShelf(params: PageQuery & { bookTitle?: string }) {
  return request.get<PageResult<BookShelfVO>>('/admin/content/bookshelf', { params })
}
