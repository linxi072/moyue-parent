package com.moyue.boot.filter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 单体形态白名单（对应网关的 WhitelistProperties）。
 *
 * @author moyue
 */
@Data
@Component
@ConfigurationProperties(prefix = "moyue.boot.security")
public class BootSecurityProperties {

    /** 免鉴权路径（Ant 风格） */
    private List<String> permitPaths = new ArrayList<>();
}
