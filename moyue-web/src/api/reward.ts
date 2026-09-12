import { get, post } from '@/utils/request';
import type { PageResult } from '@/api/types';

/** 打赏订单（对齐后端 RewardOrderEntity） */
export interface RewardOrder {
  orderNo: string;
  bookId?: number;
  chapterId?: number;
  amount: number;
  /** 支付渠道：1 微信 / 2 支付宝 */
  payChannel: number;
  /** 订单状态：0 待支付 / 1 已支付 / 2 已关闭 */
  status: number;
  payTime?: string;
  createTime?: string;
}

/** 作者稿酬流水（对齐后端 AuthorIncomeEntity） */
export interface AuthorIncome {
  id?: number;
  authorId?: number;
  bookId?: number;
  /** 来源订单号（打赏分成） */
  orderNo?: string;
  /** 收入类型：1 订阅 / 2 打赏分成 / 3 全勤奖 */
  incomeType: number;
  /** 金额（元） */
  amount: number;
  /** 结算月份 YYYY-MM */
  settleMonth?: string;
  createTime?: string;
}

/** 创建打赏订单（待支付） */
export function createReward(data: {
  bookId?: number;
  chapterId?: number;
  amount: number;
  payChannel: number;
}): Promise<RewardOrder> {
  return post<RewardOrder>('/rewards', data);
}

/** 支付打赏订单（幂等） */
export function payReward(orderNo: string): Promise<RewardOrder> {
  return post<RewardOrder>(`/rewards/${orderNo}/pay`);
}

/**
 * 我的打赏记录。
 * 后端 GET /rewards 返回的是 PageResult<RewardOrderEntity> 而非裸数组，
 * 这里取首页的最大窗口并剥出 records，以对齐 RewardOrder[] 契约。
 */
export function myRewards(): Promise<RewardOrder[]> {
  return get<PageResult<RewardOrder>>('/rewards', { page: 1, size: 100 }).then(
    (res) => (res && res.records) || []
  );
}

/** 我的稿酬流水（作者视角，分页） */
export function myIncome(page = 1, size = 20): Promise<PageResult<AuthorIncome>> {
  return get<PageResult<AuthorIncome>>('/rewards/income', { page, size });
}

/** 打赏订单详情（仅下单人可见） */
export function rewardDetail(orderNo: string): Promise<RewardOrder> {
  return get<RewardOrder>(`/rewards/${orderNo}`);
}
