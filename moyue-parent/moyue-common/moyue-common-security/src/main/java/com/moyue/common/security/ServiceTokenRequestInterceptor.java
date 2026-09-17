package com.moyue.common.security;

import com.moyue.common.Constants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 服务间内部调用令牌注入器（Feign {@link RequestInterceptor}）。
 * <p>仅对 {@code /api/v1/internal/**} 请求注入 {@link Constants#SERVICE_TOKEN_HEADER}
 * （X-Service-Token），供下游服务 {@code InternalAuthInterceptor} 验真；非内部端点
 * （走网关 {@code /api/v1/**}）不注入，避免令牌泄露与误校验。</p>
 * <p>令牌取值 {@code moyue.security.internalToken}（默认 dev 本地令牌，test/prod 由
 * {@code MOYUE_INTERNAL_TOKEN} 必注入），与下游校验侧保持一致（配置驱动优雅降级，缺失不报错）。</p>
 * <p>网关（WebFlux）无 Feign 依赖，故以 {@link ConditionalOnClass} 守卫，缺 feign 时不注册该 Bean，
 * 避免网关节点因缺少 feign 类而无法启动。</p>
 */
@Component
@ConditionalOnClass(name = "feign.RequestInterceptor")
public class ServiceTokenRequestInterceptor implements RequestInterceptor {

    /** 服务间内部调用令牌（与下游 InternalAuthInterceptor 持有的令牌一致） */
    @Value("${moyue.security.internalToken:dev-internal-token}")
    private String internalToken;

    /** 内部端点前缀 */
    private static final String INTERNAL_PREFIX = "/api/v1/internal/";

    @Override
    public void apply(RequestTemplate template) {
        if (!isInternalRequest(template) || !StringUtils.hasText(internalToken)) {
            return;
        }
        template.header(Constants.SERVICE_TOKEN_HEADER, internalToken);
    }

    /**
     * 判断当前 Feign 请求是否为内部端点。
     * RequestTemplate 在拦截器阶段已解析出相对路径（path）与完整 URL（url），
     * 二者任一命中内部前缀即视为内部调用。
     */
    private boolean isInternalRequest(RequestTemplate template) {
        String path = template.path();
        if (path != null && path.contains(INTERNAL_PREFIX)) {
            return true;
        }
        String url = template.url();
        return url != null && url.contains(INTERNAL_PREFIX);
    }
}
