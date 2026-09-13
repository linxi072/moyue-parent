package com.moyue.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.core.domain.PageResult;
import com.moyue.system.config.LogAsyncConfig;
import com.moyue.system.entity.SysLogininforEntity;
import com.moyue.system.mapper.SysLogininforMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录日志业务：异步落库 + 分页查询 + 批量删除 + 清空。
 *
 * <p>写入走 {@code @Async("logExecutor")}（LogAsyncConfig 线程池），登录埋点侧
 * 用 try-catch 包裹调用，日志故障绝不阻断登录主流程。</p>
 */
@Service
public class LogininforService {

    @Autowired
    private SysLogininforMapper logininforMapper;

    /** 异步写入登录日志（登录埋点调用） */
    @Async(LogAsyncConfig.LOG_EXECUTOR)
    public void record(SysLogininforEntity log) {
        try {
            if (log.getLoginTime() == null) {
                log.setLoginTime(LocalDateTime.now());
            }
            logininforMapper.insert(log);
        } catch (Exception ignored) {
            // 日志落库失败不影响任何业务
        }
    }

    /** 登录日志分页（可选 username / status / 时间区间过滤） */
    public PageResult<SysLogininforEntity> page(int page, int size, String username, Integer status,
                                                LocalDateTime beginTime, LocalDateTime endTime) {
        Page<SysLogininforEntity> param = new Page<>(page, size);
        LambdaQueryWrapper<SysLogininforEntity> q = Wrappers.<SysLogininforEntity>lambdaQuery();
        if (username != null && !username.isBlank()) {
            q.like(SysLogininforEntity::getUsername, username.trim());
        }
        q.eq(status != null, SysLogininforEntity::getStatus, status);
        q.ge(beginTime != null, SysLogininforEntity::getLoginTime, beginTime);
        q.le(endTime != null, SysLogininforEntity::getLoginTime, endTime);
        q.orderByDesc(SysLogininforEntity::getLoginTime);
        IPage<SysLogininforEntity> result = logininforMapper.selectPage(param, q);

        PageResult<SysLogininforEntity> pr = new PageResult<>();
        pr.setTotal(result.getTotal());
        pr.setPage(page);
        pr.setSize(size);
        pr.setRecords(result.getRecords());
        return pr;
    }

    /** 批量删除（物理删除） */
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        logininforMapper.delete(Wrappers.<SysLogininforEntity>lambdaQuery().in(SysLogininforEntity::getId, ids));
    }

    /** 清空全部登录日志（物理删除） */
    public void clear() {
        logininforMapper.delete(Wrappers.emptyWrapper());
    }
}
