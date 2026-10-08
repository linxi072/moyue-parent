package com.moyue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.redis.constant.CacheNames;
import com.moyue.common.redis.service.RedisService;
import com.moyue.system.domain.dto.query.LoginLogQuery;
import com.moyue.system.domain.entity.SysLoginLog;
import com.moyue.system.mapper.SysLoginLogMapper;
import com.moyue.system.service.SysLoginLogService;
import com.moyue.system.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 登录日志域（⑧）实现：仅追加 + 查询 + 清理，不提供修改接口。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog>
        implements SysLoginLogService {

    private static final long EXPORT_MAX = 10_000L;

    private final RedisService redisService;

    @Override
    public PageResult<SysLoginLog> pageLogs(LoginLogQuery query) {
        var page = PageUtils.<SysLoginLog>page(query);
        var result = page(page, buildWrapper(query));
        return PageUtils.toResult(result);
    }

    @Override
    public SysLoginLog detail(Long infoId) {
        SysLoginLog log = getById(infoId);
        if (log == null) {
            throw BusinessException.notFound("登录日志");
        }
        return log;
    }

    @Override
    public boolean deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return removeByIds(ids);
    }

    @Override
    public int clear() {
        LambdaQueryWrapper<SysLoginLog> wrapper =
                new LambdaQueryWrapper<SysLoginLog>().isNotNull(SysLoginLog::getId);
        long total = count(wrapper);
        remove(wrapper);
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    @Override
    public List<SysLoginLog> listForExport(LoginLogQuery query) {
        Page<SysLoginLog> page = new Page<>(1, EXPORT_MAX);
        return page(page, buildWrapper(query)).getRecords();
    }

    @Override
    public List<SysLoginLog> listByAccount(String username) {
        return list(new LambdaQueryWrapper<SysLoginLog>()
                .eq(StringUtils.hasText(username), SysLoginLog::getUsername, username)
                .orderByDesc(SysLoginLog::getLoginTime));
    }

    @Override
    public boolean unlock(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(com.moyue.common.core.exception.ErrorCode.PARAM_ERROR, "账号不能为空");
        }
        try {
            redisService.delete(CacheNames.LOGIN_FAIL + ":" + username);
        } catch (Exception e) {
            log.warn("解锁账号失败：{}", e.getMessage());
            return false;
        }
        return true;
    }

    private LambdaQueryWrapper<SysLoginLog> buildWrapper(LoginLogQuery query) {
        return new LambdaQueryWrapper<SysLoginLog>()
                .like(StringUtils.hasText(query.getUsername()), SysLoginLog::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getIp()), SysLoginLog::getIp, query.getIp())
                .eq(query.getStatus() != null, SysLoginLog::getStatus, query.getStatus())
                .ge(StringUtils.hasText(query.getBeginTime()), SysLoginLog::getLoginTime, query.getBeginTime())
                .le(StringUtils.hasText(query.getEndTime()), SysLoginLog::getLoginTime, query.getEndTime())
                .orderByDesc(SysLoginLog::getLoginTime);
    }
}
