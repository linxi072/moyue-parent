package com.moyue.common.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 客户端装配（覆盖 redisson-spring-boot-starter 的自动配置）。
 *
 * <p><b>为什么要自己装配</b>：官方 starter 读取 {@code spring.data.redis.password} 后
 * <b>不做空值判断</b>——环境变量没给密码时拿到的是空串而非 null，连接建立后照样发
 * {@code AUTH} 命令，Redis 侧没有配置 requirepass 就回报：
 * <pre>
 * ERR AUTH &lt;password&gt; called without any password configured for the default user
 * </pre>
 * 结果是「本地无密码 Redis 直接起不来」。本类在密码为空时不下发 {@code setPassword}，
 * 让有密码 / 无密码两种环境都能启动。
 *
 * <p><b>为什么保留 Redisson</b>：架构说明书规划了分布式锁（作品上下架、订单超时关单等），
 * 届时可注入 {@link RedissonClient} 直接使用；当前业务代码尚未引用，
 * 因此这里只装配、不额外引入封装。
 *
 * <p>标注 {@code @ConditionalOnMissingBean} 是为了将来若有人显式定义了
 * {@link RedissonClient}（例如接入 Redis 集群 / 哨兵），本装配自动退让。
 *
 * @author moyue
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig single = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database);
        if (StringUtils.hasText(password)) {
            single.setPassword(password);
        }
        return Redisson.create(config);
    }
}
