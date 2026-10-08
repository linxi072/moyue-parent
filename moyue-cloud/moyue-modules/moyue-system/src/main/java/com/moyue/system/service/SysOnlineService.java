package com.moyue.system.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.entity.SysUserOnline;

import java.util.List;

/**
 * 在线用户域（⑨）服务：Redis 主存会话 + sys_user_online 审计。
 *
 * @author moyue
 */
public interface SysOnlineService {

    /** 在线用户列表（分页） */
    PageResult<SysUserOnline> pageOnline(String username, String ip, int page, int size);

    /** 会话详情 */
    SysUserOnline detail(String tokenId);

    /** 强制下线（删除 Redis 会话 + 标记下线） */
    boolean kick(String tokenId);

    /** 批量强制下线 */
    int kickBatch(List<String> tokenIds);

    /** 当前在线人数 */
    long count();

    /** 登录成功后登记会话 */
    void register(SysUserOnline online);

    /** 访问续期 */
    void touch(String tokenId);
}
