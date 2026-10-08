package com.moyue.common.monitor.collector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actuator 指标采集（服务监控域）。
 *
 * <p>只暴露「在线构建器」需要的健康状态与常用指标，避免把全部 Actuator 端点透出到前端。
 *
 * @author moyue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActuatorCollector {

    /** 需要透出的指标白名单 */
    private static final String[] METRIC_WHITELIST = {
            "jvm.memory.used",
            "jvm.memory.max",
            "jvm.threads.live",
            "jvm.threads.daemon",
            "jvm.gc.pause",
            "system.cpu.usage",
            "system.load.average.1m",
            "process.uptime",
            "http.server.requests",
            "hikaricp.connections.active",
            "tomcat.threads.busy"
    };

    private final HealthEndpoint healthEndpoint;
    private final MetricsEndpoint metricsEndpoint;

    /**
     * 采集健康状态。
     *
     * @return 健康状态字符串 UP / DOWN / UNKNOWN / OUT_OF_SERVICE
     */
    public String healthStatus() {
        try {
            HealthComponent health = healthEndpoint.health();
            return health == null ? "UNKNOWN" : health.getStatus().getCode();
        } catch (Exception e) {
            log.warn("健康状态采集失败：{}", e.getMessage());
            return "UNKNOWN";
        }
    }

    /**
     * 采集健康详情。
     *
     * <p>返回 Actuator 原生 {@link HealthComponent}，包含 db / redis / diskSpace 等各组件状态，
     * 由上层按需序列化；未开启健康端点时返回 DOWN 并附错误说明。
     *
     * @return 健康详情
     */
    public HealthComponent healthDetail() {
        try {
            return healthEndpoint.health();
        } catch (Exception e) {
            return Health.down().withDetail("error", String.valueOf(e.getMessage())).build();
        }
    }

    /**
     * 采集白名单内的指标。
     *
     * <p>Spring Boot 3.2 中 {@code MetricsEndpoint#metric} 返回 {@code MetricDescriptor}，
     * 取其中的 {@code measurements}（统计名 -> 数值）扁平化后返回，便于前端直接渲染。
     *
     * @return 指标名 -> {统计名: 数值}
     */
    public Map<String, Object> metrics() {
        Map<String, Object> result = new HashMap<>();
        for (String name : METRIC_WHITELIST) {
            try {
                MetricsEndpoint.MetricDescriptor descriptor = metricsEndpoint.metric(name, null);
                if (descriptor == null || descriptor.getMeasurements() == null) {
                    continue;
                }
                Map<String, Double> measurements = new LinkedHashMap<>();
                for (MetricsEndpoint.Sample sample : descriptor.getMeasurements()) {
                    measurements.put(sample.getStatistic().getTagValueRepresentation(), sample.getValue());
                }
                result.put(name, measurements);
            } catch (Exception ignored) {
                // 指标不存在时 Actuator 抛 404，属正常情况，跳过
            }
        }
        return result;
    }
}
