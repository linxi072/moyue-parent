import { get, post, put, del } from '@/utils/request';

/**
 * 书架实体，对应后端 BookshelfEntity。
 * 注意：后端实体无 updateTime 字段，只有 createTime（加入书架时间）。
 */
export interface BookshelfEntity {
  /** 书架记录主键 */
  id: number;
  /** 读者 ID */
  userId: number;
  /** 书籍 ID */
  bookId: number;
  /** 最后阅读章节 ID */
  lastChapterId: number | null;
  /** 逻辑删除：0 否 / 1 是 */
  isDeleted: number;
  /** 加入书架时间 */
  createTime: string;
}

/** 加入书架请求（后端 AddShelfRequest 只收 bookId） */
export interface AddShelfReq {
  bookId: number;
}

/** 按用户 ID 获取书架（无分页） */
export function getBookshelf(userId: number) {
  return get<BookshelfEntity[]>(`/read/bookshelf/${userId}`);
}

/** 加入书架（幂等） */
export function addToShelf(data: AddShelfReq) {
  return post<void>('/read/bookshelf', data);
}

/** 移出书架 */
export function removeFromShelf(bookId: number) {
  return del<void>(`/read/bookshelf/${bookId}`);
}

/**
 * 更新阅读进度。
 * 后端 ProgressRequest 的字段名为 chapterId（不是 lastChapterId），这里做一次映射。
 */
export function updateProgress(bookId: number, lastChapterId: number) {
  return put<void>(`/read/bookshelf/${bookId}/progress`, { chapterId: lastChapterId });
}
