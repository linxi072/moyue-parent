package com.moyue.api.content.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

/**
 * ChapterClient 降级工厂：目标服务 moyue-content 不可用时返回统一降级响应（R.fail 40002），
 * 不抛出异常，调用方按失败业务码自行处理（对齐 RuoYi RemoteXxxFallbackFactory 模式）。
 */
@Component
public class ChapterClientFallbackFactory implements FallbackFactory<ChapterClient> {

    private static final Logger log = LoggerFactory.getLogger(ChapterClientFallbackFactory.class);

    @Override
    @SuppressWarnings("unchecked")
    public ChapterClient create(Throwable cause) {
        log.error("Feign 降级：{} 调用失败 -> {}", "moyue-content", cause.getMessage());
        return (ChapterClient) Proxy.newProxyInstance(
                ChapterClient.class.getClassLoader(),
                new Class<?>[]{ChapterClient.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (R.class.isAssignableFrom(rt)) {
                        return R.fail(ResultCode.SERVICE_DEGRADED.getCode(),
                                "服务降级：moyue-content 暂不可用，请稍后重试");
                    }
                    return null;
                });
    }
}
