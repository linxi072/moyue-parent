import { get, post } from '@/utils/request';

/** 站内通知（对齐后端 NoticeEntity） */
export interface Notice {
  id: number;
  userId: number;
  title: string;
  content: string;
  /** 通知类型：1 系统 / 2 互动 / 3 公告 */
  type: number;
  /** 是否已读：0 未读 / 1 已读 */
  isRead: number;
  createTime?: string;
}

/** 发送站内通知的请求体 */
export interface SendMessageReq {
  userId: number;
  title: string;
  content: string;
  /** 通知类型：1 系统 / 2 互动 / 3 公告 */
  type: number;
}

/** 某用户的站内通知列表（后端不分页，全量返回） */
export function listMessages(userId: number) {
  return get<Notice[]>(`/messages/${userId}`);
}

/** 发送站内通知 */
export function sendMessage(data: SendMessageReq) {
  return post<void>('/messages', data);
}
