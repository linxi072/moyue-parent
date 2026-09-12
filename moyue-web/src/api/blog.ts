import { get, post, put, del } from '@/utils/request';
import type { PageResult } from '@/api/types';

/** 博客文章，对应后端 BlogPostDTO */
export interface BlogPostDTO {
  id: number;
  authorId: number;
  authorName: string;
  title: string;
  coverUrl: string;
  summary: string;
  content: string;
  /** 0 草稿 / 1 已发布 / 2 已下架 */
  status: number;
  likeCount: number;
  commentCount: number;
  viewCount: number;
  createTime: string;
  updateTime: string;
}

/** 博客评论，对应后端 BlogCommentDTO */
export interface BlogCommentDTO {
  id: number;
  postId: number;
  userId: number;
  userName: string;
  content: string;
  likeCount: number;
  createTime: string;
}

/** 文章状态文案 */
export const BLOG_STATUS_TEXT: Record<number, string> = {
  0: '草稿',
  1: '已发布',
  2: '已下架',
};

/** 新建文章请求体（后端 CreatePostRequest） */
export interface CreatePostPayload {
  authorId: number;
  title: string;
  coverUrl?: string;
  summary?: string;
  content: string;
  /** 0 草稿 / 1 已发布 / 2 已下架 */
  status: number;
}

/** 编辑文章请求体（后端 UpdatePostRequest） */
export interface UpdatePostPayload {
  title: string;
  coverUrl?: string;
  summary?: string;
  content: string;
  status: number;
}

/**
 * 文章列表。
 * authorId 为空时查全部（博客广场）；传入则只看该作者的帖子。
 */
export function listPosts(page = 1, size = 20, authorId?: number) {
  const params: Record<string, unknown> = { page, size };
  if (authorId !== undefined && authorId > 0) {
    params.authorId = authorId;
  }
  return get<PageResult<BlogPostDTO>>('/blog/posts', params);
}

/** 文章详情（后端浏览量 +1） */
export function getPost(id: number) {
  return get<BlogPostDTO>(`/blog/posts/${id}`);
}

/** 新建文章 */
export function createPost(payload: CreatePostPayload) {
  return post<BlogPostDTO>('/blog/posts', payload);
}

/** 编辑文章 */
export function updatePost(id: number, payload: UpdatePostPayload) {
  return put<BlogPostDTO>(`/blog/posts/${id}`, payload);
}

/** 删除文章（逻辑删除） */
export function deletePost(id: number) {
  return del<void>(`/blog/posts/${id}`);
}

/** 评论列表 */
export function listComments(id: number, page = 1, size = 20) {
  return get<PageResult<BlogCommentDTO>>(`/blog/posts/${id}/comments`, { page, size });
}

/**
 * 发表评论。
 * 后端 CreateCommentRequest 除 content 外还需要 userId，故此处一并透传。
 */
export function addComment(id: number, userId: number, content: string) {
  return post<BlogCommentDTO>(`/blog/posts/${id}/comments`, { userId, content });
}

/** 点赞 / 取消点赞，返回当前点赞数 */
export function toggleLike(id: number, userId: number) {
  return post<number>(`/blog/posts/${id}/like`, { userId });
}
