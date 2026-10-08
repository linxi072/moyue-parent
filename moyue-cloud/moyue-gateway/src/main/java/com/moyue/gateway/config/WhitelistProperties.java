package com.moyue.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关白名单（外置到配置，便于不同环境调整）。
 *
 * <p>与 common-security 的 {@code moyue.security.permit-paths} 是<strong>两份独立配置</strong>：
 * 网关这份决定「不放行就直接 401」，服务内那份决定「Servlet 过滤器是否校验」。
 * 二者必须保持一致，否则会出现「网关放行、服务拦截」或反之的缝隙。
 *
 * @author moyue
 */
@Data
@Component
@ConfigurationProperties(prefix = "moyue.gateway")
public class WhitelistProperties {

    /** 免鉴权路径（Ant 风格） */
    private List<String> whitelist = new ArrayList<>();

    /** 仅允许运营主体（user_type = 3）访问的路径前缀 */
    private List<String> adminPrefixes = new ArrayList<>();
}
