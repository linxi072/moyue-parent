import { get, post } from '@/utils/request';
import type { PageResult } from '@/api/types';

/** 积分账户（对齐后端 PointsAccountDTO） */
export interface PointsAccount {
  userId: number;
  /** 当前积分余额 */
  balance: number;
  /** 累计获得 */
  totalEarned: number;
  /** 累计消费 */
  totalSpent: number;
  createTime?: string;
  updateTime?: string;
}

/** 积分商品（对齐后端 PointsProductDTO） */
export interface PointsProduct {
  id: number;
  name: string;
  description?: string;
  imageUrl?: string;
  /** 兑换所需积分 */
  costPoints: number;
  stock: number;
  /** 1 上架 / 2 下架 */
  status: number;
  createTime?: string;
  updateTime?: string;
}

/** 积分兑换订单（对齐后端 PointsOrderDTO） */
export interface PointsOrder {
  id: number;
  userId: number;
  productId: number;
  /** 商品名称快照 */
  productName: string;
  /** 兑换消耗积分 */
  costPoints: number;
  /** 0 待兑换 / 1 已兑换 / 2 已取消 */
  status: number;
  createTime?: string;
  updateTime?: string;
}

/** 查询积分账户（不存在后端会先初始化一行再返回） */
export function getAccount(userId: number) {
  return get<PointsAccount>(`/points/accounts/${userId}`);
}

/** 上架商品分页列表（后端只返回 status=1 的商品） */
export function listProducts(page = 1, size = 20) {
  return get<PageResult<PointsProduct>>('/points/products', { page, size });
}

/** 创建兑换订单 */
export function createOrder(userId: number, productId: number) {
  return post<PointsOrder>('/points/orders', { userId, productId });
}

/** 某用户的兑换订单分页列表 */
export function listOrders(userId: number, page = 1, size = 20) {
  return get<PageResult<PointsOrder>>('/points/orders', { userId, page, size });
}
