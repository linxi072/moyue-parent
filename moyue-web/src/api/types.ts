/**
 * 前端共享类型定义。
 * 与后端统一响应体 R<T> 对齐：request.ts 已剥离外层包装，
 * 因此这里描述的是 data 载荷的形状。
 */

/** MyBatis-Plus 分页结果 */
export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

/**
 * 书籍状态（对齐 BookSummaryDTO 与 book 表注释：1 连载中 / 2 已完结 / 3 已下架）。
 * 注意：这里没有 0，新建作品后端直接置 1 连载中。
 */
export const BOOK_STATUS_TEXT: Record<number, string> = {
  1: '连载中',
  2: '已完结',
  3: '已下架',
};

/** 章节状态（对齐 ChapterEntity 与 chapter 表注释：0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回） */
export const CHAPTER_STATUS_TEXT: Record<number, string> = {
  0: '草稿',
  1: '审核中',
  2: '已发布',
  3: '已驳回',
};

/** 用户角色：1 读者 / 2 作者 / 3 管理员 */
export const ROLE_TEXT: Record<number, string> = {
  1: '读者',
  2: '作者',
  3: '管理员',
};

/** 审核任务状态（对齐 AuditTaskEntity：0 待投递 / 1 已投递 / 2 已完成 / 3 死信） */
export const AUDIT_STATUS_TEXT: Record<number, string> = {
  0: '待投递',
  1: '已投递',
  2: '已完成',
  3: '死信',
};

/** 审核业务类型（对齐 AuditTaskEntity.bizType） */
export const AUDIT_BIZ_TEXT: Record<number, string> = {
  1: '章节',
  2: '评论',
};

/** 公告类型（对齐 AnnouncementEntity：1 站内公告 / 2 活动 / 3 系统维护） */
export const ANNOUNCEMENT_TYPE_TEXT: Record<number, string> = {
  1: '站内公告',
  2: '活动',
  3: '系统维护',
};

/** 公告状态（对齐 AnnouncementEntity：0 草稿 / 1 已发布 / 2 已下线） */
export const ANNOUNCEMENT_STATUS_TEXT: Record<number, string> = {
  0: '草稿',
  1: '已发布',
  2: '已下线',
};

/** 支付渠道（对齐 RewardOrderEntity：仅 1 微信 / 2 支付宝，后端未实现余额渠道） */
export const PAY_CHANNEL_TEXT: Record<number, string> = {
  1: '微信',
  2: '支付宝',
};

/** 打赏订单状态（对齐 RewardOrderEntity：0 待支付 / 1 已支付 / 2 已关闭） */
export const REWARD_STATUS_TEXT: Record<number, string> = {
  0: '待支付',
  1: '已支付',
  2: '已关闭',
};

/** 字数格式化：>= 1w 显示为 x.x 万字 */
export function formatWordCount(n: number | null | undefined): string {
  const v = Number(n || 0);
  return v >= 10000 ? (v / 10000).toFixed(1) + '万字' : v + '字';
}

/** 时间格式化：后端 LocalDateTime 直接 toString 会带 T，这里做轻量规整 */
export function formatTime(s: string | null | undefined): string {
  if (!s) return '-';
  return String(s).replace('T', ' ').slice(0, 19);
}
