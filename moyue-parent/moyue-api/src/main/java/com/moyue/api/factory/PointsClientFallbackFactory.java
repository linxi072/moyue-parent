package com.moyue.api.commerce;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

/**
 * PointsClient 降级工厂：目标服务 moyue-commerce 不可用时返回统一降级响应（R.fail 40002），
 * 不抛出异常，调用方按失败业务码自行处理（对齐 RuoYi RemoteXxxFallbackFactory 模式）。
 */
@Component
public class PointsClientFallbackFactory implements FallbackFactory<PointsClient> {

    private static final Logger log = LoggerFactory.getLogger(PointsClientFallbackFactory.class);

    @Override
    @SuppressWarnings("unchecked")
    public PointsClient create(Throwable cause) {
        log.error("Feign 降级：{} 调用失败 -> {}", "moyue-commerce", cause.getMessage());
        return (PointsClient) Proxy.newProxyInstance(
                PointsClient.class.getClassLoader(),
                new Class<?>[]{PointsClient.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (R.class.isAssignableFrom(rt)) {
                        return R.fail(ResultCode.SERVICE_DEGRADED.getCode(),
                                "服务降级：moyue-commerce 暂不可用，请稍后重试");
                    }
                    return null;
                });
    }
}
