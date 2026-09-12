import { get, put } from '@/utils/request';
import type { PageResult } from '@/api/types';

/** 用户资料（对齐后端 UserEntity 的可读字段，不含 password / isDeleted） */
export interface UserVO {
  id: number;
  phone: string;
  nickname: string;
  /** 角色：1 读者 / 2 作者 / 3 管理员 */
  role: number;
  /** 状态：0 禁用 / 1 正常 */
  status: number;
  avatarUrl?: string;
  createTime?: string;
}

/** 用户分页列表 */
export function listUsers(page = 1, size = 20): Promise<PageResult<UserVO>> {
  return get<PageResult<UserVO>>('/users', { page, size });
}

/** 按 ID 获取用户资料 */
export function getUser(id: number): Promise<UserVO> {
  return get<UserVO>(`/users/${id}`);
}

/**
 * 更新资料。
 * 后端 UpdateProfileRequest 只允许 nickname / avatarUrl 两个字段，
 * 手机号、角色、状态一律不可由本接口修改。
 */
export function updateProfile(
  id: number,
  data: { nickname?: string; avatarUrl?: string }
): Promise<UserVO> {
  return put<UserVO>(`/users/${id}`, data);
}
