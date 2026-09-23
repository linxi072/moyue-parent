package com.moyue.job.config;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * XXL-Job 执行器配置（官方标准写法）。
 * 调度中心地址、执行器名称/端口等从 application.yml 注入。
 */
@Configuration
public class XxlJobConfig {

    private static final Logger log = LoggerFactory.getLogger(XxlJobConfig.class);

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken:}")
    private String accessToken;

    @Value("${xxl.job.executor.appname}")
    private String appname;

    @Value("${xxl.job.executor.address:}")
    private String address;

    @Value("${xxl.job.executor.ip:}")
    private String ip;

    @Value("${xxl.job.executor.port:9099}")
    private int port;

    @Value("${xxl.job.executor.logpath:./logs/xxl-job}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;

    /**
     * 仅在非测试环境创建 XXL-Job 执行器。
     *
     * <p>xxl-job 的 {@link XxlJobExecutor#jobHandlerRepository} 是 JVM 级静态 Map，
     * 执行器在 {@code afterSingletonsInstantiated} 阶段会扫描并注册所有 {@code @XxlJob} handler。
     * 集成测试会并行/串行加载多个独立的 {@code @SpringBootTest} ApplicationContext（@MockBean /
     * @AutoConfigureMockMvc / @Import 各自产生不同的上下文缓存键），每个上下文都会新建自己的执行器，
     * 第二个上下文再次注册 {@code chapterPublishJob} 时即与静态 Map 中已存在的实例冲突并抛
     * "naming conflicts"。测试环境不需要真正启动执行器（不调度、不连 Admin、不绑端口），
     * 故用 {@code @Profile("!test")} 在测试 profile 下完全不创建该 bean，从源头消除跨上下文的重复注册冲突。
     * 生产 / dev 等环境照常创建唯一执行器。{@link ConditionalOnMissingBean} 作为兜底，
     * 允许外部自定义执行器时避免重复 bean。</p>
     */
    @Bean
    @Profile("!test")
    @ConditionalOnMissingBean(XxlJobExecutor.class)
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> xxl-job executor config init.");
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
