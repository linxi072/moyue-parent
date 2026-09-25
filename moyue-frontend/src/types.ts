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

/** 书籍搜索文档（ES moyue_book / BookDocument） */
export interface BookDocument {
  bookId?: number;
  title?: string;
  authorName?: string;
  categoryName?: string;
  categoryId?: number;
  coverUrl?: string;
  description?: string;
  status?: number;
  clickCount?: number;
  favoriteCount?: number;
  hotScore?: number;
  updateTime?: string;
}

/** 书籍检索结果（含纠错建议；P1-5 BookSearchResult） */
export interface BookSearchResult {
  records?: BookDocument[];
  total?: number;
  page?: number;
  size?: number;
  /** 主检索无命中、按编辑距离二次召回时的纠错建议词 */
  correctedKeyword?: string;
}

/** 书架条目（read 域 BookshelfEntity） */
export interface BookshelfItem {
  id?: number;
  userId?: number;
  bookId?: number;
  lastChapterId?: number;
  listenChapterId?: number;
  createTime?: string;
}

/** 评论 DTO（comment 域 CommentDTO） */
export interface CommentDTO {
  id?: number;
  userId?: number;
  bookId?: number;
  content?: string;
  status?: number;
  likeCount?: number;
  createTime?: string;
}

/** AI 会话（ai 域 AiSessionEntity） */
export interface AiSession {
  id?: number;
  userId?: number;
  title?: string;
  isDeleted?: number;
  createTime?: string;
  updateTime?: string;
}

/** 分类（P2-A CategoryController） */
export interface CategoryDTO {
  id?: number;
  name?: string;
  sort?: number;
  icon?: string;
  remark?: string;
}
