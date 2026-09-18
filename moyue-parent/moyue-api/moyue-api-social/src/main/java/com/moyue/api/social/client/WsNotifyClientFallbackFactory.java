package com.moyue.api.social.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

/**
 * WsNotifyClient 降级工厂：目标服务 moyue-social 不可用时返回统一降级响应
 * （R.fail(40002) = SERVICE_DEGRADED），不抛异常；调用方（BookshelfWsNotifier）按失败业务码
 * 自行忽略，绝不阻断阅读主流程。对齐既有 RiskClient / ImClient 的 FallbackFactory 写法。
 */
@Component
public class WsNotifyClientFallbackFactory implements FallbackFactory<WsNotifyClient> {

    private static final Logger log = LoggerFactory.getLogger(WsNotifyClientFallbackFactory.class);

    @Override
    @SuppressWarnings("unchecked")
    public WsNotifyClient create(Throwable cause) {
        log.error("Feign 降级：{} 调用失败 -> {}", "moyue-social", cause.getMessage());
        return (WsNotifyClient) Proxy.newProxyInstance(
                WsNotifyClient.class.getClassLoader(),
                new Class<?>[]{WsNotifyClient.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (R.class.isAssignableFrom(rt)) {
                        return R.fail(ResultCode.SERVICE_DEGRADED.getCode(),
                                "服务降级：moyue-social 暂不可用，书架推送降级跳过");
                    }
                    return null;
                });
    }
}
