package com.moyue.common.log.sink;

import com.moyue.common.log.model.LoginLogDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * 空实现：未接入 moyue-system 时静默丢弃。
 *
 * @author moyue
 */
@Slf4j
public class NoopLoginLogSink implements LoginLogSink {

    @Override
    public void save(LoginLogDTO dto) {
        log.debug("[login-log] 未接入持久化实现，丢弃：{}", dto.getUsername());
    }
}
