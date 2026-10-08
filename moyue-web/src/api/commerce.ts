import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { PageQuery } from './types'

export interface PayOrderVO {
  id: number
  orderNo: string
  userId?: number
  productName?: string
  productType?: number
  amount?: number
  quantity?: number
  payChannel?: number
  status?: number
  tradeNo?: string
  createTime?: string
  remark?: string
}

export interface ProductVO {
  id: number
  name: string
  type?: number
  priceAmount?: number
  points?: number
  stock?: number
  status?: number
  sort?: number
  createTime?: string
  remark?: string
}

export interface PointsAccountVO {
  id: number
  userId?: number
  balance?: number
  totalIncome?: number
  totalConsume?: number
  frozen?: number
  createTime?: string
  remark?: string
}

export interface PointsLogVO {
  id: number
  userId?: number
  bizType?: number
  changeAmount?: number
  balanceAfter?: number
  refId?: string
  remark?: string
  createTime?: string
}

export interface PayOrderQuery extends PageQuery {
  orderNo?: string
  userId?: number
  productType?: number
  status?: number
}

export interface ProductQuery extends PageQuery {
  name?: string
  type?: number
  status?: number
}

export interface PointsAccountQuery extends PageQuery {
  userId?: number
}

export interface PointsLogQuery extends PageQuery {
  userId?: number
  bizType?: number
}

// 付费订单
export function pageOrders(params: PayOrderQuery) {
  return request.get<PageResult<PayOrderVO>>('/admin/commerce/orders', { params })
}
export function summaryOrders() {
  return request.get<Record<string, any>>('/admin/commerce/orders/summary')
}
export function refundOrder(id: number) {
  return request.post<boolean>(`/admin/commerce/orders/${id}/refund`)
}

// 兑换商品
export function pageProducts(params: ProductQuery) {
  return request.get<PageResult<ProductVO>>('/admin/commerce/products', { params })
}
export function createProduct(data: Partial<ProductVO>) {
  return request.post<number>('/admin/commerce/products', data)
}
export function updateProduct(id: number, data: Partial<ProductVO>) {
  return request.put<boolean>(`/admin/commerce/products/${id}`, data)
}
export function deleteProduct(id: number) {
  return request.delete<boolean>(`/admin/commerce/products/${id}`)
}
export function onlineProduct(id: number) {
  return request.post<boolean>(`/admin/commerce/products/${id}/online`)
}
export function offlineProduct(id: number) {
  return request.post<boolean>(`/admin/commerce/products/${id}/offline`)
}
export function exchangeProduct(id: number, userId: number) {
  return request.post<number>(`/admin/commerce/products/${id}/exchange`, null, { params: { userId } })
}

// 积分
export function pagePointsAccounts(params: PointsAccountQuery) {
  return request.get<PageResult<PointsAccountVO>>('/admin/commerce/points', { params })
}
export function pagePointsLogs(params: PointsLogQuery) {
  return request.get<PageResult<PointsLogVO>>('/admin/commerce/points/logs', { params })
}
export function adjustPoints(data: { userId: number; bizType: number; amount: number; remark?: string }) {
  return request.post<number>(`/admin/commerce/points/adjust`, null, { params: data })
}
