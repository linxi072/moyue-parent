package com.moyue.common.cache;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

/**
 * 测试专用本地缓存管理器：让 {@code @Cacheable} / {@code @CacheEvict} 在集成测试中真正生效，
 * 从而能断言「缓存命中（下游只查一次）」与「写后失效（下游再查一次）」语义。
 *
 * <p>生产默认走 Redis（{@link MoyueCacheAutoConfiguration#moyueCacheManager}）或 Caffeine 兜底；
 * 但集成测试环境无运行中的 Redis，自动配置的 RedisCacheManager 会因连接失败退化为 no-op
 * （每次调用都回源、从不命中），无法验证缓存语义。本配置注入一个真实进程内
 * {@link ConcurrentMapCacheManager}，优先级高于自动配置同名 bean
 * （{@code @ConditionalOnMissingBean(CacheManager.class)} 生效），使缓存命中测试有意义。</p>
 */
@TestConfiguration
public class LocalCacheTestConfig {

    @Bean
    public CacheManager localTestCacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
