package com.moyue.api.social.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

/**
 * DynamicClient 降级工厂：moyue-social 不可用时返回 {@code R.fail(40002)}，不抛异常；
 * 调用方（content / system 事件监听器）按失败业务码降级跳过，不阻断发布 / 打款主流程。
 */
@Component
public class DynamicClientFallbackFactory implements FallbackFactory<DynamicClient> {

    private static final Logger log = LoggerFactory.getLogger(DynamicClientFallbackFactory.class);

    @Override
    @SuppressWarnings("unchecked")
    public DynamicClient create(Throwable cause) {
        log.error("Feign 降级：{} 调用失败 -> {}", "moyue-social(dynamicClient)", cause.getMessage());
        return (DynamicClient) Proxy.newProxyInstance(
                DynamicClient.class.getClassLoader(),
                new Class<?>[]{DynamicClient.class},
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
