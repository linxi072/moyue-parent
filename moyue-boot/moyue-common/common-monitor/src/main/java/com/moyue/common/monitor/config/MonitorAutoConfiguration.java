package com.moyue.common.monitor.config;

import com.moyue.common.monitor.collector.ActuatorCollector;
import com.moyue.common.monitor.collector.DruidCollector;
import com.moyue.common.monitor.collector.RedisCollector;
import com.moyue.common.monitor.collector.ServerCollector;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 监控模块自动装配。
 *
 * <p>三个采集器按「依赖是否存在」条件装配，缺依赖时对应监控域自动降级（页面提示不可用），
 * 不会导致应用启动失败。
 *
 * @author moyue
 */
@AutoConfiguration(after = {HealthEndpointAutoConfiguration.class, MetricsEndpointAutoConfiguration.class})
public class MonitorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServerCollector serverCollector() {
        return new ServerCollector();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.alibaba.druid.pool.DruidDataSource")
    public DruidCollector druidCollector() {
        return new DruidCollector();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StringRedisTemplate.class)
    public RedisCollector redisCollector(StringRedisTemplate template) {
        return new RedisCollector(template);
    }

    /**
     * Actuator 采集器。
     *
     * <p>必须同时校验 HealthEndpoint 与 MetricsEndpoint 两个 <b>bean</b> 是否存在，不能只看类：
     * Spring Boot 3 的 {@code MetricsEndpointAutoConfiguration} 带 {@code @ConditionalOnAvailableEndpoint}，
     * 当服务的 {@code management.endpoints.web.exposure.include} 未包含 metrics 时该 bean 根本不会创建，
     * 仅用 {@code @ConditionalOnClass} 会导致注入失败、应用启动直接崩溃（Cloud 各服务踩过此坑）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({org.springframework.boot.actuate.health.HealthEndpoint.class,
                        org.springframework.boot.actuate.metrics.MetricsEndpoint.class})
    public ActuatorCollector actuatorCollector(org.springframework.boot.actuate.health.HealthEndpoint healthEndpoint,
                                               org.springframework.boot.actuate.metrics.MetricsEndpoint metricsEndpoint) {
        return new ActuatorCollector(healthEndpoint, metricsEndpoint);
    }
}
