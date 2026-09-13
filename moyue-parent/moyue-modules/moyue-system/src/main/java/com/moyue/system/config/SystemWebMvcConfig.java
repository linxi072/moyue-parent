package com.moyue.system.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * moyue-system 模块 MVC 装配：注册令牌黑名单拦截器。
 * <p>全局拦截器（HeaderInterceptor / AdminRoleInterceptor / CORS）由 common 的
 * {@code com.moyue.WebMvcConfig} 注册，本类只补充模块级防线：</p>
 * <ul>
 *   <li>{@link TokenDenyInterceptor} —— /api/v1/admin/** 上校验强退黑名单
 *       {@code moyue:auth:deny:{md5(token)}}，命中即 401。</li>
 * </ul>
 */
@Configuration
public class SystemWebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TokenDenyInterceptor tokenDenyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 强退黑名单：仅后台接口需要（登录接口与 C 端不涉及）
        registry.addInterceptor(tokenDenyInterceptor).addPathPatterns("/api/v1/admin/**");
    }
}
