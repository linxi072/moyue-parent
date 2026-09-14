package com.moyue.common.core.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 墨阅小说网 · springdoc-openapi 公共配置属性。
 *
 * <p>各业务服务统一经 {@code moyue-common-core} 的自动配置获得 OpenAPI 元信息，
 * 无需每个服务重复声明。属性以 {@code moyue.openapi} 为前缀，均提供合理默认值，
 * 绝大多数服务可直接零配置启用。</p>
 */
@ConfigurationProperties(prefix = "moyue.openapi")
public class MoyueOpenApiProperties {

    /** 是否启用契约自动配置，默认开启。 */
    private boolean enabled = true;

    /** springdoc 暴露的 api-docs 路径，默认 /v3/api-docs。 */
    private String apiDocsPath = "/v3/api-docs";

    /** 文档标题；为空时回退为 spring.application.name。 */
    private String title;

    /** 文档版本；为空时回退为 info.app.version 或 1.0.0-SNAPSHOT。 */
    private String version;

    /** 文档描述。 */
    private String description = "墨阅小说网 OpenAPI 契约";

    /** 服务 baseUrl；为空时回退为 http://localhost:${server.port}。 */
    private String serverUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiDocsPath() {
        return apiDocsPath;
    }

    public void setApiDocsPath(String apiDocsPath) {
        this.apiDocsPath = apiDocsPath;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }
}
