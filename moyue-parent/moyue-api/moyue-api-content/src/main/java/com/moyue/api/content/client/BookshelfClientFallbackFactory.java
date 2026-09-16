package com.moyue.api.content.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;
import java.util.Collections;

/**
 * BookshelfClient 降级工厂：目标服务 moyue-content 不可用时返回空列表（非异常），
 * 调用方（个性化推荐）据此退化为热门推荐，不阻断主流程（对齐 BookClientFallbackFactory 模式）。
 */
@Component
public class BookshelfClientFallbackFactory implements FallbackFactory<BookshelfClient> {

    private static final Logger log = LoggerFactory.getLogger(BookshelfClientFallbackFactory.class);

    @Override
    @SuppressWarnings("unchecked")
    public BookshelfClient create(Throwable cause) {
        log.error("Feign 降级：{} 调用失败 -> {}", "moyue-content", cause.getMessage());
        return (BookshelfClient) Proxy.newProxyInstance(
                BookshelfClient.class.getClassLoader(),
                new Class<?>[]{BookshelfClient.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (R.class.isAssignableFrom(rt)) {
                        // 降级返回空书架：调用方退化为热门推荐
                        return R.ok(Collections.emptyList());
                    }
                    return null;
                });
    }
}
