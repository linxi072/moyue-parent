package com.moyue.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关启动类（8080，仅 cloud 仓）。
 *
 * <p><b>为什么要排除 Security 自动配置</b>：网关是 WebFlux 栈，而 common-security 为
 * Servlet 栈编写（依赖 spring-boot-starter-security）。若不排除，Boot 会自动装配
 * 默认表单登录与 HTTP Basic，导致所有路由 401。网关自身不做认证决策，只做
 * 「解析 JWT → 注入身份头」，鉴权仍下沉到各服务（见 JwtAuthGlobalFilter）。
 *
 * @author moyue
 */
@EnableDiscoveryClient
@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration.class
})
public class GatewayApplication {

    public static void main(String[] args) {
        // 必须显式声明 REACTIVE：common-security 传递引入了 spring-boot-starter-web，
        // Boot 会据此把应用推断成 Servlet 应用，让网关跑在 Tomcat 上（WebFlux on Servlet）。
        // 该模式下没有 Netty 的 WebSocket 升级能力，/ws/im 代理会以 500 失败。
        // yml 的 spring.main.web-application-type 不足以覆盖推断结果，只能在启动器上强制指定。
        new org.springframework.boot.builder.SpringApplicationBuilder(GatewayApplication.class)
                .web(org.springframework.boot.WebApplicationType.REACTIVE)
                .run(args);
    }
}
