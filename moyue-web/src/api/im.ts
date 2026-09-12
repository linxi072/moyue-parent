import { get, post } from '@/utils/request';
import type { PageResult } from '@/api/types';

/** 会话，对应后端 ConversationDTO（单聊 title 为空，前端按成员展示） */
export interface ConversationDTO {
  id: number;
  /** 1 单聊 / 2 群聊 */
  type: number;
  title: string;
  ownerId: number;
  memberIds: number[] | null;
  lastMessagePreview: string;
  lastMessageTime: string;
  createTime: string;
  updateTime: string;
}

/** 聊天消息，对应后端 MessageDTO */
export interface MessageDTO {
  id: number;
  conversationId: number;
  senderId: number;
  senderName: string;
  content: string;
  /** 1 文本 / 2 图片 / 3 系统 */
  type: number;
  /** 0 已发送 / 1 已读 */
  status: number;
  createTime: string;
}

/** 会话类型文案 */
export const CONVERSATION_TYPE_TEXT: Record<number, string> = {
  1: '单聊',
  2: '群聊',
};

/** 消息类型：1 文本 / 2 图片 / 3 系统 */
export const MESSAGE_TYPE_TEXT: Record<number, string> = {
  1: '文本',
  2: '图片',
  3: '系统',
};

/** 新建会话请求体（后端 CreateConversationRequest） */
export interface CreateConversationPayload {
  type: number;
  title?: string;
  /** 创建人 / 群主；后端实体必填，前端取当前登录用户 */
  ownerId: number;
  memberIds: number[];
}

/** 发送消息请求体（后端 SendMessageRequest） */
export interface SendMessagePayload {
  senderId: number;
  content: string;
  type: number;
}

/** 我参与的会话列表（按最近消息时间倒序） */
export function listConversations(userId: number, page = 1, size = 20) {
  return get<PageResult<ConversationDTO>>('/im/conversations', { userId, page, size });
}

/** 会话详情 */
export function getConversation(conversationId: number) {
  return get<ConversationDTO>(`/im/conversations/${conversationId}`);
}

/** 新建会话（单聊 / 群聊） */
export function createConversation(payload: CreateConversationPayload) {
  return post<ConversationDTO>('/im/conversations', payload);
}

/** 会话消息（按发送时间升序） */
export function listMessages(conversationId: number, page = 1, size = 50) {
  return get<PageResult<MessageDTO>>(`/im/conversations/${conversationId}/messages`, { page, size });
}

/**
 * 发送消息。
 * 已知技术债 16-4：后端 WebSocket 不经网关，故前端统一走 REST，
 * 页面侧用轮询拉取新消息。
 */
export function sendMessage(conversationId: number, payload: SendMessagePayload) {
  return post<MessageDTO>(`/im/conversations/${conversationId}/messages`, payload);
}
