package com.moyue.boot.config;

import com.moyue.boot.filter.BootAuthFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 单体形态的 Web 配置：CORS + 身份过滤器注册。
 *
 * <p>与网关 {@code CorsConfig} 行为保持一致：允许凭证、开发期不限制源。
 *
 * @author moyue
 */
@Configuration
@ConditionalOnProperty(prefix = "moyue", name = "mode", havingValue = "boot")
public class BootWebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowCredentials(true)
                .allowedOriginPatterns("*")
                .allowedHeaders("*")
                .allowedMethods("*")
                .exposedHeaders("X-User-Id", "X-User-Type");
    }

    @Bean
    public FilterRegistrationBean<BootAuthFilter> bootAuthFilterRegistration(BootAuthFilter filter) {
        FilterRegistrationBean<BootAuthFilter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setOrder(100);
        bean.setName("bootAuthFilter");
        return bean;
    }
}
