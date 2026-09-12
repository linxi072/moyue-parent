import { get } from '@/utils/request';

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

export interface PageResult<T> {
  total: number;
  page: number;
  size: number;
  records: T[];
}

/** 分页查询书籍 */
export function listBooks(page = 1, size = 20) {
  return get<PageResult<BookVO>>('/books', { page, size });
}

/** 书籍详情 */
export function bookDetail(bookId: number) {
  return get<BookVO>(`/books/${bookId}`);
}
