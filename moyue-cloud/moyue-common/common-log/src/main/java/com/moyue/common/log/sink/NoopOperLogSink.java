package com.moyue.common.log.sink;

import com.moyue.common.log.model.OperLogDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * 空实现：未接入 moyue-system 时静默丢弃，仅 debug 输出。
 *
 * @author moyue
 */
@Slf4j
public class NoopOperLogSink implements OperLogSink {

    @Override
    public void save(OperLogDTO dto) {
        log.debug("[oper-log] 未接入持久化实现，丢弃：{} {}", dto.getTitle(), dto.getOperUrl());
    }
}
