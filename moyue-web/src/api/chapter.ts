import { get } from '@/utils/request';
import type { PageResult } from '@/api/types';

/**
 * 章节实体，对应后端 ChapterEntity。
 * 注意：后端实体无 updateTime 字段，仅有 createTime / publishTime / isDeleted。
 */
export interface ChapterEntity {
  /** 章节主键 */
  id: number;
  /** 作品 ID */
  bookId: number;
  /** 章节序号 */
  chapterNo: number;
  /** 章节标题 */
  title: string;
  /** 章节正文 */
  content: string;
  /** 本章字数 */
  wordCount: number;
  /** 状态：0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回 */
  status: number;
  /** 逻辑删除：0 否 / 1 是 */
  isDeleted: number;
  /** 发布时间 */
  publishTime: string;
  /** 创建时间 */
  createTime: string;
}

/** 作品目录分页（后端按 chapter_no 升序，不做状态过滤） */
export function listChapters(bookId: number, page = 1, size = 20) {
  return get<PageResult<ChapterEntity>>('/chapters', { bookId, page, size });
}

/** 草稿箱分页（后端固定查 status=0） */
export function listDrafts(bookId: number, page = 1, size = 20) {
  return get<PageResult<ChapterEntity>>('/chapters/drafts', { bookId, page, size });
}

/** 章节详情（含正文） */
export function getChapter(chapterId: number) {
  return get<ChapterEntity>(`/chapters/${chapterId}`);
}
