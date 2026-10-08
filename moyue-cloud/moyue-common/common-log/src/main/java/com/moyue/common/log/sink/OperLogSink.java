package com.moyue.common.log.sink;

import com.moyue.common.log.model.OperLogDTO;

/**
 * 操作日志落库出口（SPI）。
 *
 * <p>common-log 只负责采集，不持有 Mapper；由 moyue-system 提供实现并注册为 Bean，
 * 未注册时走 {@link NoopOperLogSink} 静默丢弃，保证其它模块引入 common-log 也能启动。
 *
 * @author moyue
 */
public interface OperLogSink {

    /**
     * 落库一条操作日志。
     *
     * @param log 日志对象
     */
    void save(OperLogDTO log);
}
