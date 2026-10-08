package com.moyue.common.log.sink;

import com.moyue.common.log.model.LoginLogDTO;

/**
 * 登录日志落库出口（SPI），实现方式同 {@link OperLogSink}。
 *
 * @author moyue
 */
public interface LoginLogSink {

    /**
     * 落库一条登录日志。
     *
     * @param log 登录日志
     */
    void save(LoginLogDTO log);
}
