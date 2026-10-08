package com.moyue.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 强制网关使用 Netty 作为 WebFlux 容器。
 *
 * <p><b>背景</b>：{@code common-security} 传递引入了 {@code spring-boot-starter-web}，
 * 于是 classpath 上同时存在 Tomcat 与 Reactor Netty。Boot 的
 * {@code ReactiveWebServerFactoryAutoConfiguration} 会优先装配
 * {@code TomcatReactiveWebServerFactory}，网关因此变成「跑在 Tomcat 上的 WebFlux」。
 *
 * <p><b>后果</b>：{@code ReactorNettyRequestUpgradeStrategy} 在 Servlet 容器里无法完成
 * WebSocket 握手，{@code /ws/im} 代理必定以 HTTP 500 失败。显式提供 Netty 工厂后，
 * 自动配置的 {@code @ConditionalOnMissingBean(ReactiveWebServerFactory)} 让位，
 * 网关恢复为真正的 Netty 容器，WS 代理随之可用。
 *
 * @author moyue
 */
@Configuration
public class NettyWebServerConfig {

    @Bean
    @ConditionalOnMissingBean(ReactiveWebServerFactory.class)
    public NettyReactiveWebServerFactory nettyReactiveWebServerFactory() {
        return new NettyReactiveWebServerFactory();
    }
}
