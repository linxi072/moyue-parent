package com.moyue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.redis.constant.CacheNames;
import com.moyue.common.redis.service.RedisService;
import com.moyue.system.domain.entity.SysUserOnline;
import com.moyue.system.mapper.SysUserOnlineMapper;
import com.moyue.system.service.SysOnlineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 在线用户域（⑨）实现。
 *
 * <p>会话以 {@code moyue:online:token:{tokenId}} 存 Redis，每次访问续期；
 * {@code sys_user_online} 供审计与历史查询。强踢后标记 status = 0，
 * 网关侧需拒绝该 token（见架构说明书「广播下线通知」要求，本版以 Redis 失效 + 状态标记实现）。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOnlineServiceImpl implements SysOnlineService {

    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private final SysUserOnlineMapper onlineMapper;
    private final RedisService redisService;

    private static String key(String tokenId) {
        return CacheNames.ONLINE_TOKEN + ":" + tokenId;
    }

    @Override
    public PageResult<SysUserOnline> pageOnline(String username, String ip, int page, int size) {
        var p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysUserOnline>(page, size);
        var result = onlineMapper.selectPage(p, new LambdaQueryWrapper<SysUserOnline>()
                .like(StringUtils.hasText(username), SysUserOnline::getUsername, username)
                .like(StringUtils.hasText(ip), SysUserOnline::getIp, ip)
                .eq(SysUserOnline::getStatus, 1)
                .orderByDesc(SysUserOnline::getLastAccessTime));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public SysUserOnline detail(String tokenId) {
        SysUserOnline online = onlineMapper.selectOne(
                new LambdaQueryWrapper<SysUserOnline>().eq(SysUserOnline::getTokenId, tokenId));
        if (online == null) {
            throw BusinessException.notFound("会话");
        }
        return online;
    }

    @Override
    public boolean kick(String tokenId) {
        try {
            redisService.delete(key(tokenId));
        } catch (Exception e) {
            log.warn("会话缓存删除失败：{}", e.getMessage());
        }
        SysUserOnline update = new SysUserOnline();
        update.setStatus(0);
        return onlineMapper.update(update, new LambdaQueryWrapper<SysUserOnline>()
                .eq(SysUserOnline::getTokenId, tokenId)) > 0;
    }

    @Override
    public int kickBatch(List<String> tokenIds) {
        if (tokenIds == null || tokenIds.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (String tokenId : tokenIds) {
            if (kick(tokenId)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public long count() {
        Long count = onlineMapper.selectCount(new LambdaQueryWrapper<SysUserOnline>()
                .eq(SysUserOnline::getStatus, 1));
        return count == null ? 0L : count;
    }

    @Override
    public void register(SysUserOnline online) {
        online.setStatus(1);
        online.setLastAccessTime(LocalDateTime.now());
        onlineMapper.insert(online);
        try {
            redisService.set(key(online.getTokenId()), online.getTokenId(), SESSION_TTL);
        } catch (Exception e) {
            log.warn("会话缓存写入失败：{}", e.getMessage());
        }
    }

    @Override
    public void touch(String tokenId) {
        try {
            if (redisService.hasKey(key(tokenId))) {
                redisService.expire(key(tokenId), SESSION_TTL.toMillis(),
                        java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("会话续期失败：{}", e.getMessage());
        }
        SysUserOnline update = new SysUserOnline();
        update.setLastAccessTime(LocalDateTime.now());
        onlineMapper.update(update, new LambdaQueryWrapper<SysUserOnline>()
                .eq(SysUserOnline::getTokenId, tokenId));
    }
}
