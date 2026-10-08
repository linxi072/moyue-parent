package com.moyue.common.log.config;

import com.moyue.common.log.aspect.LogAspect;
import com.moyue.common.log.sink.LoginLogSink;
import com.moyue.common.log.sink.NoopLoginLogSink;
import com.moyue.common.log.sink.NoopOperLogSink;
import com.moyue.common.log.sink.OperLogSink;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 日志模块自动装配。
 *
 * <p>{@code OperLogSink} / {@code LoginLogSink} 使用 {@code @ConditionalOnMissingBean}，
 * moyue-system 提供真实实现后本空实现自动失效。
 *
 * @author moyue
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "moyue.log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OperLogSink operLogSink() {
        return new NoopOperLogSink();
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginLogSink loginLogSink() {
        return new NoopLoginLogSink();
    }

    @Bean
    @ConditionalOnMissingBean
    public LogAspect logAspect(OperLogSink sink) {
        return new LogAspect(sink);
    }
}
