package com.moyue.api.system.client;

import com.moyue.api.system.dto.AuthorIncomeDTO;
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
 * IncomeClient 降级工厂：目标服务 moyue-system 不可用时返回统一降级响应（R.fail 40002），
 * 列表类查询降级为空集合，不抛异常，调用方按失败业务码自行兜底（对齐 SettlementClientFallbackFactory 模式）。
 */
@Component
public class IncomeClientFallbackFactory implements FallbackFactory<IncomeClient> {

    private static final Logger log = LoggerFactory.getLogger(IncomeClientFallbackFactory.class);

    @Override
    @SuppressWarnings("unchecked")
    public IncomeClient create(Throwable cause) {
        log.error("Feign 降级：{} 调用失败 -> {}", "moyue-system", cause.getMessage());
        return (IncomeClient) Proxy.newProxyInstance(
                IncomeClient.class.getClassLoader(),
                new Class<?>[]{IncomeClient.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (R.class.isAssignableFrom(rt)) {
                        if (List.class.equals(method.getReturnType())) {
                            return R.ok(Collections.<AuthorIncomeDTO>emptyList());
                        }
                        return R.fail(ResultCode.SERVICE_DEGRADED.getCode(),
                                "服务降级：moyue-system 暂不可用，请稍后重试");
                    }
                    return null;
                });
    }
}
