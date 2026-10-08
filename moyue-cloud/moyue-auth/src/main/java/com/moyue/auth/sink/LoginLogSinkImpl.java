package com.moyue.auth.sink;

import com.moyue.auth.domain.entity.SysLoginLog;
import com.moyue.auth.mapper.AuthLoginLogMapper;
import com.moyue.common.log.model.LoginLogDTO;
import com.moyue.common.log.sink.LoginLogSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 登录日志落库实现（auth 侧）。
 *
 * <p>与操作日志同理：异步写入、异常不外抛 —— 日志失败不应阻断登录主链路。
 *
 * @author moyue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLogSinkImpl implements LoginLogSink {

    private final AuthLoginLogMapper loginLogMapper;

    @Override
    @Async
    public void save(LoginLogDTO dto) {
        if (dto == null) {
            return;
        }
        try {
            SysLoginLog entity = new SysLoginLog();
            entity.setUsername(dto.getUsername());
            entity.setUserId(dto.getUserId());
            entity.setUserType(dto.getUserType());
            entity.setIp(dto.getIp());
            entity.setLocation(dto.getLocation());
            entity.setBrowser(dto.getBrowser());
            entity.setOs(dto.getOs());
            entity.setStatus(dto.getStatus());
            entity.setMessage(dto.getMessage());
            entity.setLoginTime(dto.getLoginTime() == null
                    ? java.time.LocalDateTime.now() : dto.getLoginTime());
            loginLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("登录日志落库失败：username={}, err={}", dto.getUsername(), e.getMessage());
        }
    }
}
