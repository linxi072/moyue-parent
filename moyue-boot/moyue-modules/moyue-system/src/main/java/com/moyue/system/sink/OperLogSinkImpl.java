package com.moyue.system.sink;

import com.moyue.common.log.model.OperLogDTO;
import com.moyue.common.log.sink.OperLogSink;
import com.moyue.system.domain.entity.SysOperLog;
import com.moyue.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志落库实现（注册为 Bean 后 common-log 的空实现自动失效）。
 *
 * <p>架构说明书要求「异步落库，避免拖慢主流程」，故用 {@code @Async}；
 * 落库失败只记 warn，绝不回抛影响业务。
 *
 * @author moyue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperLogSinkImpl implements OperLogSink {

    private final SysOperLogMapper operLogMapper;

    @Override
    @Async
    public void save(OperLogDTO dto) {
        try {
            operLogMapper.insert(toEntity(dto));
        } catch (Exception e) {
            log.warn("操作日志落库失败：{}", e.getMessage());
        }
    }

    private SysOperLog toEntity(OperLogDTO dto) {
        SysOperLog entity = new SysOperLog();
        entity.setTitle(dto.getTitle());
        entity.setBusinessType(dto.getBusinessType());
        entity.setMethod(dto.getMethod());
        entity.setRequestMethod(dto.getRequestMethod());
        entity.setOperatorType(dto.getOperatorType());
        entity.setOperName(dto.getOperName());
        entity.setOperId(dto.getOperId());
        entity.setOperUrl(dto.getOperUrl());
        entity.setOperIp(dto.getOperIp());
        entity.setOperLocation(dto.getOperLocation());
        entity.setOperParam(dto.getOperParam());
        entity.setJsonResult(dto.getJsonResult());
        entity.setStatus(dto.getStatus());
        entity.setErrorMsg(dto.getErrorMsg());
        entity.setCostTime(dto.getCostTime());
        entity.setOperTime(dto.getOperTime());
        return entity;
    }
}
