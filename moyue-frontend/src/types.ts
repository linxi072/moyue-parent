// 墨阅小说网前端 · 共享类型定义
// 字段严格对齐后端 R<T> 与跨服务 DTO（BookSummaryDTO / ChapterDTO / AiMessageEntity / LoginVO）。

/** 统一响应体 R<T>：code=0 成功，其余为业务错误码 */
export interface ApiResponse<T> {
  code: number;
  message?: string;
  data?: T;
  traceId?: string;
}

/** 登录返回体 */
export interface LoginVO {
  accessToken: string;
  refreshToken: string;
}

/** 书籍概要 DTO（跨服务共享） */
export interface BookSummaryDTO {
  bookId?: number;
  authorId?: number;
  title?: string;
  author?: string;
  wordCount?: number;
  category?: string;
  /** 1 连载中 / 2 已完结 / 3 已下架 */
  status?: number;
  coverUrl?: string;
  intro?: string;
  clickCount?: number;
}

/** 章节 DTO（跨服务共享） */
export interface ChapterDTO {
  id?: number;
  bookId?: number;
  chapterNo?: number;
  title?: string;
  wordCount?: number;
  /** 仅单章查询填充正文；列表接口留空 */
  content?: string;
  /** 0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回 */
  status?: number;
  publishTime?: string;
}

/** AI 客服消息实体 */
export interface AiMessageEntity {
  id?: number;
  sessionId?: number;
  /** 1 用户提问 / 2 助手回复 */
  role?: number;
  content?: string;
  createTime?: string;
}

/** 分页结果（对齐 com.moyue.common.core.domain.PageResult） */
export interface PageResult<T> {
  total?: number;
  page?: number;
  size?: number;
  records?: T[];
}
