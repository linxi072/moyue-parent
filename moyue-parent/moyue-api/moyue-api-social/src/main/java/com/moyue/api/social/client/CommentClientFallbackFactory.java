package com.moyue.api.social.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

/**
 * CommentClient 降级工厂：目标服务 moyue-social 不可用时返回统一降级响应（R.fail 40002），
 * 不抛出异常，调用方按失败业务码自行处理（对齐 RuoYi RemoteXxxFallbackFactory 模式）。
 */
@Component
public class CommentClientFallbackFactory implements FallbackFactory<CommentClient> {

    private static final Logger log = LoggerFactory.getLogger(CommentClientFallbackFactory.class);

    @Override
    @SuppressWarnings("unchecked")
    public CommentClient create(Throwable cause) {
        log.error("Feign 降级：{} 调用失败 -> {}", "moyue-social", cause.getMessage());
        return (CommentClient) Proxy.newProxyInstance(
                CommentClient.class.getClassLoader(),
                new Class<?>[]{CommentClient.class},
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
