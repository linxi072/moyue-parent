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

/** 当前用户资料（auth 域 UserInfoVO） */
export interface UserInfoVO {
  id?: number;
  phone?: string;
  nickname?: string;
  /** 1 读者 / 2 作者 / 3 管理员 */
  role?: number;
  /** 0 禁用 / 1 正常 */
  status?: number;
}

/** 积分账户（commerce 域 PointsAccountDTO） */
export interface PointsAccountDTO {
  userId?: number;
  /** 当前积分余额 */
  balance?: number;
  /** 累计获得 */
  totalEarned?: number;
  /** 累计消费 */
  totalSpent?: number;
  createTime?: string;
  updateTime?: string;
}

/** 积分商品（commerce 域 PointsProductDTO） */
export interface PointsProductDTO {
  id?: number;
  name?: string;
  description?: string;
  imageUrl?: string;
  /** 兑换所需积分 */
  costPoints?: number;
  stock?: number;
  /** 1 上架 / 2 下架 */
  status?: number;
  createTime?: string;
}

/** 积分兑换订单（commerce 域 PointsOrderDTO） */
export interface PointsOrderDTO {
  id?: number;
  userId?: number;
  productId?: number;
  /** 商品名称快照 */
  productName?: string;
  costPoints?: number;
  /** 0 待兑换 / 1 已兑换 / 2 已取消 */
  status?: number;
  createTime?: string;
}

/** 积分流水（points 域 PointsFlowEntity）：bizType 1 签到 / 2 阅读 / 3 评论 / 4 系统 / 5 兑换消费 */
export interface PointsFlowEntity {
  id?: number;
  userId?: number;
  bizType?: number;
  /** 正获得 / 负消费 */
  points?: number;
  remark?: string;
  createTime?: string;
}
