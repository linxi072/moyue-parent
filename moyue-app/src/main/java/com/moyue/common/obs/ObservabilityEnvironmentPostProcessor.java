package com.moyue.common.obs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 可观测性默认配置（最低优先级，非覆盖式）：
 * 仅当用户未显式配置对应项时生效，使各服务无需逐个改 yml 即获得：
 *  1) Prometheus 指标端点暴露（management.endpoints.web.exposure.include）
 *  2) 单行 JSON 日志（logging.pattern.console / logging.pattern.file），
 *     含 traceId / userId / service 字段，Filebeat → ES 可按 json 解析检索。
 *
 * 注意：刻意不通过库内 logback-spring.xml 提供日志格式——
 * 库 JAR 根目录的 logback 配置会抢占所有消费者的日志配置且易引发 XML 解析问题，
 * 改用 logging.pattern.* 属性由 Spring Boot 默认 logback 基底应用，零侵入、可覆盖。
 */
public class ObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String EXPOSURE_KEY = "management.endpoints.web.exposure.include";
    private static final String CONSOLE_PATTERN_KEY = "logging.pattern.console";
    private static final String FILE_PATTERN_KEY = "logging.pattern.file";

    /**
     * 单行 JSON 日志格式。
     * - %X{traceId:-} / %X{userId:-}：取自 TraceIdFilter 写入的 MDC，缺失时为 null 字段；
     * - %replace(%msg){'\n',' '}：将换行压成空格，保证一条日志一行（异常栈不再跨行），
     *   严格双引号转义可由后续接入 logstash encoder 增强，此处保持零外部依赖。
     */
    private static final String JSON_PATTERN =
            "{\"@timestamp\":\"%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX}\","
            + "\"level\":\"%level\","
            + "\"service\":\"${spring.application.name:-moyue}\","
            + "\"traceId\":\"%X{traceId:-}\","
            + "\"userId\":\"%X{userId:-}\","
            + "\"logger\":\"%logger\","
            + "\"thread\":\"%thread\","
            + "\"message\":\"%replace(%msg){'\\n',' '}\"}%n";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> map = new HashMap<>();
        // 非覆盖式：仅填充用户未显式配置的项
        if (!environment.containsProperty(EXPOSURE_KEY)) {
            map.put(EXPOSURE_KEY, "prometheus,health,info");
        }
        if (!environment.containsProperty(CONSOLE_PATTERN_KEY)) {
            map.put(CONSOLE_PATTERN_KEY, JSON_PATTERN);
        }
        if (!environment.containsProperty(FILE_PATTERN_KEY)) {
            map.put(FILE_PATTERN_KEY, JSON_PATTERN);
        }
        if (map.isEmpty()) {
            return;
        }
        // addLast → 最低优先级，排在 application.yml / 环境变量 / 命令行参数之后
        environment.getPropertySources().addLast(new MapPropertySource("moyue-observability-defaults", map));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
