package com.moyue.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 跨域与后台鉴权配置（作用于 auth / book 等业务服务）。
 * 网关侧跨域由 gateway 的 globalcors 统一处理；后台角色拦截见 {@link AdminRoleInterceptor}。
 * 仅当应用为 Servlet（MVC）环境时才加载，避免污染 WebFlux 网关。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AdminRoleInterceptor adminRoleInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 后台接口（/api/v1/admin/**）仅允许 role=3 管理员访问，关闭 16-6 越权风险
        registry.addInterceptor(adminRoleInterceptor)
                .addPathPatterns("/api/v1/admin/**");
    }
}
