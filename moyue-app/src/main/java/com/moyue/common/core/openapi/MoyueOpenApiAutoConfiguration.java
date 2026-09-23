package com.moyue.common.core.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * 墨阅小说网 · 统一 OpenAPI 元信息自动配置。
 *
 * <p>双守卫保证只在「Servlet Web 应用」且「已引入 springdoc」时激活：
 * <ul>
 *     <li>{@code @ConditionalOnWebApplication(type = SERVLET)}：WebFlux 网关（非 Servlet）不激活；</li>
 *     <li>{@code @ConditionalOnClass(OpenAPI.class)}：未引入 springdoc 的模块（如网关）不激活；</li>
 *     <li>{@code @ConditionalOnProperty(moyue.openapi.enabled)}：可一键关闭。</li>
 * </ul>
 * 激活后统一注入 {@link OpenAPI} Bean，包含 title/version/servers 与 bearer-JWT 安全方案。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
@ConditionalOnProperty(name = "moyue.openapi.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MoyueOpenApiProperties.class)
public class MoyueOpenApiAutoConfiguration {

    /**
     * 构建统一 OpenAPI 契约元信息。
     *
     * @param props 配置属性
     * @param env   环境（用于读取服务名、端口、应用版本）
     * @return 装配完成的 {@link OpenAPI}
     */
    @Bean
    public OpenAPI moyueOpenAPI(MoyueOpenApiProperties props, Environment env) {
        String name = env.getRequiredProperty("spring.application.name");
        String ver = props.getVersion() != null ? props.getVersion()
                : env.getProperty("info.app.version", "1.0.0-SNAPSHOT");
        String server = props.getServerUrl() != null ? props.getServerUrl()
                : "http://localhost:" + env.getProperty("server.port", "8080");
        return new OpenAPI()
                .info(new Info().title(props.getTitle() != null ? props.getTitle() : name)
                        .version(ver).description(props.getDescription()))
                .servers(List.of(new Server().url(server).description(name)))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
