package com.moyue.api.social.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

/**
 * FollowClient 降级工厂（契约预留）：moyue-social 不可用时返回 {@code R.fail(40002)}，不抛异常。
 */
@Component
public class FollowClientFallbackFactory implements FallbackFactory<FollowClient> {

    private static final Logger log = LoggerFactory.getLogger(FollowClientFallbackFactory.class);

    @Override
    @SuppressWarnings("unchecked")
    public FollowClient create(Throwable cause) {
        log.error("Feign 降级：{} 调用失败 -> {}", "moyue-social(followClient)", cause.getMessage());
        return (FollowClient) Proxy.newProxyInstance(
                FollowClient.class.getClassLoader(),
                new Class<?>[]{FollowClient.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (R.class.isAssignableFrom(rt)) {
                        return R.fail(ResultCode.SERVICE_DEGRADED.getCode(),
                                "服务降级：moyue-social 暂不可用，请稍后重试");
                    }
                    return null;
                });
    }
}
