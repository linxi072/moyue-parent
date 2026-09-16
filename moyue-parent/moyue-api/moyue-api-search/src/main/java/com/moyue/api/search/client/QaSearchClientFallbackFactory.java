package com.moyue.api.search.client;

import com.moyue.api.search.dto.QaContextDTO;
import com.moyue.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

/**
 * QaSearchClient 降级工厂：检索服务不可用时返回空知识库（R.ok，passages 为空），
 * 不抛异常；LLM 引擎退化为无上下文生成，对话主流程不受影响。
 */
@Slf4j
@Component
public class QaSearchClientFallbackFactory implements FallbackFactory<QaSearchClient> {

    @Override
    @SuppressWarnings("unchecked")
    public QaSearchClient create(Throwable cause) {
        log.warn("Feign 降级：{} 问答检索调用失败 -> {}", "moyue-search", cause == null ? "unknown" : cause.getMessage());
        return (QaSearchClient) Proxy.newProxyInstance(
                QaSearchClient.class.getClassLoader(),
                new Class<?>[]{QaSearchClient.class},
                (proxy, method, args) -> {
                    if (R.class.isAssignableFrom(method.getReturnType())) {
                        return R.ok(new QaContextDTO());
                    }
                    return null;
                });
    }
}
