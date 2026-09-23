package com.moyue.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.core.domain.PageResult;
import com.moyue.system.config.LogAsyncConfig;
import com.moyue.system.entity.SysOperLogEntity;
import com.moyue.system.mapper.SysOperLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志业务：异步落库 + 分页查询 + 批量删除。
 *
 * <p>写入走 {@code @Async("logExecutor")}（LogAsyncConfig 线程池），与业务线程隔离；
 * 队列满时拒绝策略丢弃日志，绝不反压业务主链路。</p>
 */
@Service
public class OperLogService {

    @Autowired
    private SysOperLogMapper operLogMapper;

    /** 异步写入操作日志（切面调用，异常只吞不打断线程池） */
    @Async(LogAsyncConfig.LOG_EXECUTOR)
    public void record(SysOperLogEntity operLog) {
        try {
            if (operLog.getOperTime() == null) {
                operLog.setOperTime(LocalDateTime.now());
            }
            operLogMapper.insert(operLog);
        } catch (Exception ignored) {
            // 日志落库失败不影响任何业务（连日志都不该抛出去打断线程池）
        }
    }

    /** 操作日志分页（可选 module / operatorName / status / 时间区间过滤） */
    public PageResult<SysOperLogEntity> page(int page, int size, String module, String operatorName,
                                             Integer status, LocalDateTime beginTime, LocalDateTime endTime) {
        Page<SysOperLogEntity> param = new Page<>(page, size);
        LambdaQueryWrapper<SysOperLogEntity> q = Wrappers.<SysOperLogEntity>lambdaQuery();
        if (module != null && !module.isBlank()) {
            q.like(SysOperLogEntity::getModule, module.trim());
        }
        if (operatorName != null && !operatorName.isBlank()) {
            q.like(SysOperLogEntity::getOperatorName, operatorName.trim());
        }
        q.eq(status != null, SysOperLogEntity::getStatus, status);
        q.ge(beginTime != null, SysOperLogEntity::getOperTime, beginTime);
        q.le(endTime != null, SysOperLogEntity::getOperTime, endTime);
        q.orderByDesc(SysOperLogEntity::getOperTime);
        IPage<SysOperLogEntity> result = operLogMapper.selectPage(param, q);

        PageResult<SysOperLogEntity> pr = new PageResult<>();
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
        operLogMapper.delete(Wrappers.<SysOperLogEntity>lambdaQuery().in(SysOperLogEntity::getId, ids));
    }
}
