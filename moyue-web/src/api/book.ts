import { get, post, put, del } from '@/utils/request';
import type { PageResult } from '@/api/types';

/**
 * 分页结果类型统一从 @/api/types 取，此处仅为兼容既有引用保留。
 * 新代码请直接 import type { PageResult } from '@/api/types'。
 */
export type { PageResult };

/** 作品状态：唯一真源在 @/api/types，这里转出一份避免页面各引各的 */
export { BOOK_STATUS_TEXT } from '@/api/types';

export interface BookVO {
  bookId: number;
  title: string;
  author: string;
  wordCount: number;
  category: string;
  status: number;
  coverUrl: string;
  intro: string;
}

/** 分页查询书籍 */
export function listBooks(page = 1, size = 20) {
  return get<PageResult<BookVO>>('/books', { page, size });
}

/** 书籍详情 */
export function bookDetail(bookId: number) {
  return get<BookVO>(`/books/${bookId}`);
}

// ------------------------------ 作者端：作品管理 ------------------------------

/** 作品分类字典：后端 BookService 硬编码（1 玄幻 / 2 都市 / 3 悬疑），前端照抄 */
export const BOOK_CATEGORY_OPTIONS: { value: number; label: string }[] = [
  { value: 1, label: '玄幻' },
  { value: 2, label: '都市' },
  { value: 3, label: '悬疑' },
];

/** 分类名 → 分类 ID（DTO 只回传 category 名称，编辑时用它反查回填） */
export const BOOK_CATEGORY_ID_BY_NAME: Record<string, number> = {
  玄幻: 1,
  都市: 2,
  悬疑: 3,
};

/**
 * 作品概要，对应后端 BookSummaryDTO。
 * categoryId / tags / createTime 后端 DTO 暂未回传，故标为可选：
 * categoryId 由分类名反查兜底，createTime 缺失时页面展示「-」。
 */
export interface BookSummaryDTO {
  bookId: number;
  authorId: number;
  title: string;
  author: string;
  wordCount: number;
  category: string;
  status: number;
  coverUrl: string;
  intro: string;
  categoryId?: number;
  tags?: string;
  createTime?: string;
}

/** 新建作品请求体（title / categoryId 必填） */
export interface CreateBookPayload {
  title: string;
  coverUrl?: string;
  categoryId: number;
  tags?: string;
  intro?: string;
}

/** 编辑作品请求体（字段均可选，后端仅更新非空字段） */
export interface UpdateBookPayload {
  title?: string;
  coverUrl?: string;
  categoryId?: number;
  tags?: string;
  intro?: string;
  status?: number;
}

/** 我的作品：authorId 取自网关注入头，前端无法伪造 */
export function listMyBooks(page = 1, size = 20) {
  return get<PageResult<BookSummaryDTO>>('/books/mine', { page, size });
}

/** 新建作品 */
export function createBook(payload: CreateBookPayload) {
  return post<BookSummaryDTO>('/books', payload);
}

/** 编辑作品 */
export function updateBook(bookId: number, payload: UpdateBookPayload) {
  return put<BookSummaryDTO>(`/books/${bookId}`, payload);
}

/** 删除作品（逻辑删除） */
export function deleteBook(bookId: number) {
  return del<void>(`/books/${bookId}`);
}
