package com.moyue.common.obs;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 可观测性自动装配（仅 Servlet 环境生效；WebFlux 网关由自身 TraceIdGlobalFilter 处理）：
 * - 注册 TraceIdFilter（最高优先级附近，请求最早进入即写入 MDC）；
 * - 注册 TraceIdFeignInterceptor（透传 traceId/userId）；
 * - 受 moyue.observability.enabled 开关控制（默认启用，缺失即启用，配置驱动降级）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "moyue.observability.enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityAutoConfiguration {

    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(TraceIdFilter filter) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public TraceIdFeignInterceptor traceIdFeignInterceptor() {
        return new TraceIdFeignInterceptor();
    }
}
