import { get, post, put, del } from '@/utils/request';
import type { PageResult } from '@/api/types';

/* ------------------------------ 审核 audit ------------------------------ */

/**
 * 审核任务实体（对应后端 AuditTaskEntity，audit_task 本地消息表）。
 * status：0 待投递 / 1 已投递 / 2 已完成 / 3 死信。
 */
export interface AuditTaskEntity {
  id: number;
  bizType: number;
  bizId: number;
  status: number;
  retryCount: number;
  createTime: string;
  updateTime: string;
}

/** 查询审核任务，status 为空查全部：GET /admin/audit/tasks?status= */
export function listAuditTasks(status?: number) {
  return get<AuditTaskEntity[]>('/admin/audit/tasks', { status });
}

/** 查询待审评论（status=0 的待投递任务）：GET /admin/audit/comments */
export function listPendingComments() {
  return get<AuditTaskEntity[]>('/admin/audit/comments');
}

/** 审核通过：PUT /admin/audit/tasks/{taskId}/approve */
export function approveTask(taskId: number) {
  return put<AuditTaskEntity>(`/admin/audit/tasks/${taskId}/approve`);
}

/** 审核驳回：PUT /admin/audit/tasks/{taskId}/reject */
export function rejectTask(taskId: number) {
  return put<AuditTaskEntity>(`/admin/audit/tasks/${taskId}/reject`);
}

/* --------------------------- 公告 announcement --------------------------- */

/**
 * 运营公告实体（对应后端 AnnouncementEntity）。
 * type：1 站内公告 / 2 活动 / 3 系统维护；status：0 草稿 / 1 已发布 / 2 已下线。
 */
export interface AnnouncementEntity {
  id: number;
  title: string;
  content: string;
  type: number;
  status: number;
  isTop: number;
  publishTime: string;
  isDeleted: number;
  createTime: string;
  updateTime: string;
}

/** 新建/编辑公告的请求体（后端 AnnouncementRequest） */
export interface AnnouncementPayload {
  title: string;
  content: string;
  type: number;
  status: number;
  isTop: number;
}

/**
 * 公告分页：GET /admin/announcements?page=&size=&status=
 * 注意：后端返回的是 PageResult 分页对象，而非裸数组。
 */
export function listAnnouncements(page = 1, size = 20, status?: number) {
  return get<PageResult<AnnouncementEntity>>('/admin/announcements', { page, size, status });
}

/** 新建公告：POST /admin/announcements */
export function createAnnouncement(data: AnnouncementPayload) {
  return post<AnnouncementEntity>('/admin/announcements', data);
}

/** 编辑公告（仅更新非空字段）：PUT /admin/announcements/{id} */
export function updateAnnouncement(id: number, data: AnnouncementPayload) {
  return put<AnnouncementEntity>(`/admin/announcements/${id}`, data);
}

/** 删除公告（逻辑删除）：DELETE /admin/announcements/{id} */
export function deleteAnnouncement(id: number) {
  return del<void>(`/admin/announcements/${id}`);
}

/* ---------------------------- 订单 operation ---------------------------- */

/**
 * 打赏订单实体（对应后端 RewardOrderEntity）。
 * payChannel：1 微信 / 2 支付宝 / 3 余额；status：0 待支付 / 1 已支付 / 2 已关闭。
 */
export interface RewardOrderEntity {
  id: number;
  orderNo: string;
  userId: number;
  bookId: number;
  chapterId: number;
  amount: number;
  payChannel: number;
  status: number;
  payTime: string;
  createTime: string;
  isDeleted: number;
}

/** 打赏订单分页（对账）：GET /admin/orders?page=&size= */
export function listAdminOrders(page = 1, size = 20) {
  return get<PageResult<RewardOrderEntity>>('/admin/orders', { page, size });
}

/* ------------------------------ 统计 stat ------------------------------ */

/** 全站聚合统计：GET /admin/stats/overview（键名由后端决定，前端不硬编码） */
export function statsOverview() {
  return get<Record<string, number>>('/admin/stats/overview');
}

/* ----------------------------- 积分 points ----------------------------- */

/**
 * 积分商品（对应后端 PointsProductDTO）。
 * status：1 上架 / 2 下架。
 */
export interface PointsProductDTO {
  id: number;
  name: string;
  description: string;
  imageUrl: string;
  costPoints: number;
  stock: number;
  status: number;
  createTime: string;
  updateTime: string;
}

/** 管理端商品分页（含下架）：GET /admin/points/products?page=&size= */
export function listAdminProducts(page = 1, size = 20) {
  return get<PageResult<PointsProductDTO>>('/admin/points/products', { page, size });
}

/** 管理端新建商品：POST /admin/points/products */
export function createProduct(data: PointsProductDTO) {
  return post<PointsProductDTO>('/admin/points/products', data);
}

/** 管理端更新商品：PUT /admin/points/products/{id} */
export function updateProduct(id: number, data: PointsProductDTO) {
  return put<PointsProductDTO>(`/admin/points/products/${id}`, data);
}
