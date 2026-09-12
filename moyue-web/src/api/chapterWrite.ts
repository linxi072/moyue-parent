import { post, put, del } from '@/utils/request';
import type { ChapterEntity } from '@/api/chapter';

/**
 * 作者端章节写接口。
 * 读接口（目录 / 草稿箱）复用 @/api/chapter，此处统一再导出，
 * 页面只需从本模块引入，避免实体与状态字典出现两份定义。
 */
export { listChapters, listDrafts } from '@/api/chapter';
export type { ChapterEntity } from '@/api/chapter';

/** 章节状态：唯一真源在 @/api/types（0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回），此处转出 */
export { CHAPTER_STATUS_TEXT } from '@/api/types';

/** 新建章节请求体（后端 CreateChapterRequest） */
export interface CreateChapterPayload {
  bookId: number;
  title: string;
  content: string;
  chapterNo: number;
  /** 0 草稿 / 1 审核中；不传则后端默认 0 草稿 */
  status: number;
}

/** 编辑章节请求体（后端 UpdateChapterRequest，字段均可选，仅更新非空字段） */
export interface UpdateChapterPayload {
  title?: string;
  content?: string;
  chapterNo?: number;
  status?: number;
  /** 定时发布时间，LocalDateTime 字符串 */
  publishTime?: string;
}

/** 新建章节 */
export function createChapter(payload: CreateChapterPayload) {
  return post<ChapterEntity>('/chapters', payload);
}

/** 编辑章节 */
export function updateChapter(chapterId: number, payload: UpdateChapterPayload) {
  return put<ChapterEntity>(`/chapters/${chapterId}`, payload);
}

/** 删除章节（逻辑删除） */
export function deleteChapter(chapterId: number) {
  return del<void>(`/chapters/${chapterId}`);
}

/**
 * 发布章节。
 * publishTime 为空表示立即发布；传入未来时间则置为「审核中」等待定时发布。
 */
export function publishChapter(chapterId: number, publishTime?: string) {
  return post<ChapterEntity>(`/chapters/${chapterId}/publish`, {
    publishTime: publishTime || null,
  });
}

/** 章节排序：修改章节序号 */
export function reorderChapter(chapterId: number, chapterNo: number) {
  return put<ChapterEntity>(`/chapters/${chapterId}/order`, { chapterNo });
}
