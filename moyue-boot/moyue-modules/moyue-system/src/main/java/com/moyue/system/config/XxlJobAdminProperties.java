package com.moyue.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * XXL-Job Admin 连接配置（ADR-13：复用 XXL-Job，只做管理界面）。
 *
 * <p>本域不提供任务增删，任务由开发用 {@code @XxlJob} 声明后由 Admin 注册，
 * 避免前端配置 Cron 与代码不同步。
 *
 * <p><b>为什么显式加 {@code @Component}</b>：{@code @ConfigurationProperties} 只声明前缀，
 * 不会把类注册成 Bean——common-security 的两个 Properties 是靠
 * {@code SecurityAutoConfiguration} 上的 {@code @EnableConfigurationProperties} 带进来的，
 * 本类没有对应入口，缺少它会在启动时报
 * 「required a bean of type XxlJobAdminProperties that could not be found」。
 *
 * @author moyue
 */
@Data
@Component
@ConfigurationProperties(prefix = "moyue.xxl-job.admin")
public class XxlJobAdminProperties {

    /** 是否启用 Admin 对接，关闭后定时任务域降级为只读空列表 */
    private boolean enabled = true;

    /** Admin 根地址，如 http://127.0.0.1:8080/xxl-job-admin */
    private String address = "http://127.0.0.1:8080/xxl-job-admin";

    /** 登录账号 */
    private String username = "admin";

    /** 登录密码 */
    private String password = "123456";

    /** 连接超时（毫秒） */
    private int connectTimeout = 3000;

    /** 读取超时（毫秒） */
    private int readTimeout = 5000;

    /** 应用名称（执行器 appname），用于过滤本应用任务，为空表示不过滤 */
    private String appName = "";
}
