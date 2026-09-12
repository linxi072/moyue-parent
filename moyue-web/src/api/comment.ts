import { get, post, del } from '@/utils/request';
import type { PageResult } from '@/api/types';

/**
 * 评论实体，对应后端 CommentEntity。
 * 评论人由后端取网关注入的 X-User-Id，前端不传 userId。
 */
export interface CommentEntity {
  /** 评论主键 */
  id: number;
  /** 评论人 ID */
  userId: number;
  /** 作品 ID */
  bookId: number;
  /** 章节 ID，书评为 null */
  chapterId: number | null;
  /** 评论内容 */
  content: string;
  /** 机审风险分 */
  auditScore: number | null;
  /** 状态：0 待审 / 1 已通过 / 2 已驳回 */
  status: number;
  /** 点赞数 */
  likeCount: number;
  /** 逻辑删除：0 否 / 1 是 */
  isDeleted: number;
  /** 创建时间 */
  createTime: string;
}

/** 发表评论请求 */
export interface CreateCommentReq {
  bookId: number;
  chapterId?: number;
  content: string;
}

/** 按书籍分页查询评论 */
export function listComments(bookId: number, page = 1, size = 20) {
  return get<PageResult<CommentEntity>>('/comments', { bookId, page, size });
}

/** 发表评论 */
export function addComment(data: CreateCommentReq) {
  return post<CommentEntity>('/comments', data);
}

/** 删除评论（本人 / 管理员） */
export function deleteComment(commentId: number) {
  return del<void>(`/comments/${commentId}`);
}

/**
 * 点赞切换。
 * 后端 CommentService.toggleLike 返回切换后的 like_count（不是 0/1 状态位）。
 */
export function toggleLike(commentId: number) {
  return post<number>(`/comments/${commentId}/like`);
}
