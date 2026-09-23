package com.moyue.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 日志异步线程池配置。
 * <p>操作日志 / 登录日志的落库走 {@code @Async("logExecutor")}，与业务线程隔离：</p>
 * <ul>
 *   <li>有界队列（1000）：防止日志洪峰把内存打爆；</li>
 *   <li>拒绝策略为丢弃并告警日志：日志属于旁路设施，宁可丢日志也绝不拖垮业务主链路；</li>
 *   <li>核心 2 / 最大 4：日志写入为低频轻量操作，够用且省资源。</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class LogAsyncConfig {

    /** 线程池 Bean 名称，@Async 引用 */
    public static final String LOG_EXECUTOR = "logExecutor";

    @Bean(LOG_EXECUTOR)
    public Executor logExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("oper-log-");
        // 拒绝时丢弃当前日志并打印告警：日志不反压业务线程
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                // 仅告警不抛出：与"日志故障不影响业务"的硬性要求一致
                org.slf4j.LoggerFactory.getLogger(LogAsyncConfig.class)
                        .warn("[oper-log] 日志队列已满，丢弃一条日志记录（queue={}/{}）",
                                e.getQueue().size(), e.getQueue().size() + e.getMaximumPoolSize());
            }
        });
        // 应用关闭时等待队列中的日志写完，避免停机丢日志
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
