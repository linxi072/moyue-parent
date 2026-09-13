package com.moyue.api.account.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

/**
 * UserClient 降级工厂：目标服务 moyue-account 不可用时返回统一降级响应（R.fail 40002），
 * 不抛出异常，调用方按失败业务码自行处理（对齐 RuoYi RemoteXxxFallbackFactory 模式）。
 */
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    private static final Logger log = LoggerFactory.getLogger(UserClientFallbackFactory.class);

    @Override
    @SuppressWarnings("unchecked")
    public UserClient create(Throwable cause) {
        log.error("Feign 降级：{} 调用失败 -> {}", "moyue-account", cause.getMessage());
        return (UserClient) Proxy.newProxyInstance(
                UserClient.class.getClassLoader(),
                new Class<?>[]{UserClient.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (R.class.isAssignableFrom(rt)) {
                        return R.fail(ResultCode.SERVICE_DEGRADED.getCode(),
                                "服务降级：moyue-account 暂不可用，请稍后重试");
                    }
                    return null;
                });
    }
}
