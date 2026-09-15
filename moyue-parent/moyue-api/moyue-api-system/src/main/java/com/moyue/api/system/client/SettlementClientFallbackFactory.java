package com.moyue.api.system.client;

import com.moyue.api.system.dto.SettlementDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

/**
 * SettlementClient 降级工厂：目标服务 moyue-system 不可用时返回统一降级响应（R.fail 40002），
 * 不抛出异常，调用方按失败业务码自行处理（对齐 PointsClientFallbackFactory 模式）。
 */
@Component
public class SettlementClientFallbackFactory implements FallbackFactory<SettlementClient> {

    private static final Logger log = LoggerFactory.getLogger(SettlementClientFallbackFactory.class);

    @Override
    @SuppressWarnings("unchecked")
    public SettlementClient create(Throwable cause) {
        log.error("Feign 降级：{} 调用失败 -> {}", "moyue-system", cause.getMessage());
        return (SettlementClient) Proxy.newProxyInstance(
                SettlementClient.class.getClassLoader(),
                new Class<?>[]{SettlementClient.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (R.class.isAssignableFrom(rt)) {
                        // 列表类查询降级为空集合，单对象查询降级为 null，由调用方兜底
                        if (List.class.equals(method.getReturnType())) {
                            return R.ok(Collections.<SettlementDTO>emptyList());
                        }
                        return R.fail(ResultCode.SERVICE_DEGRADED.getCode(),
                                "服务降级：moyue-system 暂不可用，请稍后重试");
                    }
                    return null;
                });
    }
}
