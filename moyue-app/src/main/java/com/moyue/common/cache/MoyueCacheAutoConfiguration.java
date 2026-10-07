package com.moyue.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 全服务可复用的缓存自动配置（P2-16 第一刀 + 性能与缓存兜底增强）。
 * <p>引入 moyue-common 且 Classpath 存在 spring-data-redis、且<br>
 * {@code moyue.cache.enabled=true}（默认开启）时生效。</p>
 * <p>两级缓存实现：</p>
 * <ul>
 *   <li><b>Redis 实现</b>：容器中存在 {@link RedisConnectionFactory} 时启用
 *       （{@code @ConditionalOnBean}），按缓存名集中配置 TTL；开启统计以暴露
 *       {@code cache.gets{result=hit|miss}} 给 Prometheus。</li>
 *   <li><b>本地有界兜底</b>：Redis 不可用（ConnectionFactory 缺失 / 未引入 starter）时启用
 *       Caffeine 有界缓存，替换 Spring 默认的 {@code ConcurrentMapCacheManager}
 *       （无界、无 TTL，存在内存泄漏隐患）。全局 10 分钟 TTL + 1 万条上限，
 *       降级场景可接受。</li>
 * </ul>
 * <p>设计要点（Redis 模式）：</p>
 * <ul>
 *   <li>Key 统一前缀 {@code moyue:}，避免多服务共用同一 Redis 库时相互覆盖；</li>
 *   <li>值为 Jackson JSON（注册 JavaTimeModule、关闭时间戳写法），便于运维直接查看缓存内容；</li>
 *   <li>开启默认类型信息（{@code DefaultTyping.NON_FINAL}）以还原 {@code PageResult<T>} 这类泛型结果；</li>
 *   <li>不缓存 null，防止缓存穿透式空值长期占位；</li>
 *   <li>所有缓存异常只记 warn 日志，绝不外抛——Redis 不可用时自动回源数据库，业务不中断。</li>
 * </ul>
 * <p>运维可通过 {@code moyue.cache.enabled=false} 一键关闭（默认开启）。</p>
 */
@AutoConfiguration(after = RedisAutoConfiguration.class, before = CacheAutoConfiguration.class)
@EnableCaching
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(name = "moyue.cache.enabled", havingValue = "true", matchIfMissing = true)
public class MoyueCacheAutoConfiguration implements CachingConfigurer {

    /** 全站缓存 key 前缀，隔离其它系统共用同一 Redis 实例 */
    private static final String KEY_PREFIX = "moyue:";

    /** 未显式配置 TTL 的缓存默认 10 分钟 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /** 本地兜底缓存上限：防止 ConcurrentMap 无界增长导致 OOM */
    private static final long LOCAL_MAX_SIZE = 10_000L;

    /** 本地兜底缓存统一 TTL（降级场景退化为统一策略） */
    private static final Duration LOCAL_TTL = Duration.ofMinutes(10);

    /**
     * Redis 实现：仅当容器中存在 {@link RedisConnectionFactory} 时生效。
     * 与下方 Caffeine 兜底互斥（均带 {@code @ConditionalOnMissingBean(CacheManager.class)}，
     * 本方法声明在前，Redis 存在时优先创建）。
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(CacheManager.class)
    public RedisCacheManager moyueCacheManager(RedisConnectionFactory connectionFactory) {
        // 非加锁写入器：缓存不做分布式锁，配合较短 TTL 换取吞吐（章节/书架均为低频写、高频读）
        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);

        RedisCacheConfiguration defaults = defaultConfig(DEFAULT_TTL);

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put(CacheNames.BOOK_LIST, defaultConfig(Duration.ofMinutes(5)));
        perCache.put(CacheNames.BOOK_DETAIL, defaultConfig(Duration.ofMinutes(10)));
        perCache.put(CacheNames.CHAPTER_CATALOG, defaultConfig(Duration.ofMinutes(10)));
        perCache.put(CacheNames.CHAPTER_CONTENT, defaultConfig(Duration.ofMinutes(30)));
        perCache.put(CacheNames.READ_BOOKSHELF, defaultConfig(Duration.ofMinutes(5)));
        perCache.put(CacheNames.RECOMMEND, defaultConfig(Duration.ofMinutes(5)));
        perCache.put(CacheNames.LEADERBOARD, defaultConfig(Duration.ofMinutes(5)));
        perCache.put(CacheNames.PAID_ENTITLEMENT, defaultConfig(Duration.ofMinutes(5)));
        perCache.put(CacheNames.CATEGORY, defaultConfig(Duration.ofMinutes(10)));
        perCache.put(CacheNames.INBOX, defaultConfig(Duration.ofMinutes(2)));
        perCache.put(CacheNames.AUTHOR_DASHBOARD, defaultConfig(Duration.ofMinutes(5)));
        perCache.put(CacheNames.AUTHOR_DASHBOARD, defaultConfig(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(writer)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                // 开启统计：暴露 cache.gets{result=hit|miss} / cache.puts / cache.evictions 给 Prometheus
                .enableStatistics()
                .build();
    }

    /**
     * 本地有界兜底：Redis 不可用（ConnectionFactory 缺失 / 未引入 starter）时启用，
     * 替换 Spring 默认的 {@code ConcurrentMapCacheManager}（无界、无 TTL，存在内存泄漏隐患）。
     * 全局 10 分钟 TTL + 1 万条上限；逐缓存差异化 TTL 在 Redis 模式下已按名配置，
     * 兜底模式退化为统一策略（降级场景可接受）。缓存按名动态创建，无需预注册。
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CaffeineCacheManager moyueLocalCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(LOCAL_MAX_SIZE)
                .expireAfterWrite(LOCAL_TTL));
        return manager;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new WarnOnlyCacheErrorHandler();
    }

    private static RedisCacheConfiguration defaultConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .computePrefixWith(cacheName -> KEY_PREFIX + cacheName + "::")
                // 不缓存 null：查不到的章节/书架每次回源，避免空值长期占位
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper())));
    }

    /**
     * 缓存专用 ObjectMapper：注册 JavaTimeModule 处理 LocalDateTime，并开启默认类型信息。
     * <p>开启类型信息的原因：分页结果 {@code PageResult<List<ChapterEntity>>} 经 JSON 往返后会退化为
     * LinkedHashMap，必须携带类型信息才能还原为具体类型。缓存数据由本服务写入，Redis 位于内网可信边界，
     * 故采用 LaissezFaireSubTypeValidator；若后续要将缓存开放给外部写入，应改为按 cacheName 定制
     * 具体 JavaType 的序列化器以消除多态反序列化风险。</p>
     */
    private static ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        return mapper;
    }

    /**
     * 缓存异常处理器：Redis 抖动 / 序列化失败时只打 warn 日志，不向上抛出异常，保证自动回源数据库。
     * 这是缓存作为旁路加速设施的硬性要求——缓存故障不得影响主链路可用性。
     */
    private static class WarnOnlyCacheErrorHandler implements CacheErrorHandler {

        private static final Logger log = LoggerFactory.getLogger(WarnOnlyCacheErrorHandler.class);

        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            log.warn("[cache] 读取缓存失败，回源数据库：cache={}, key={}", cache.getName(), key, exception);
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            log.warn("[cache] 写入缓存失败，忽略：cache={}, key={}", cache.getName(), key, exception);
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log.warn("[cache] 删除缓存失败，忽略：cache={}, key={}", cache.getName(), key, exception);
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log.warn("[cache] 清空缓存失败，忽略：cache={}", cache.getName(), exception);
        }
    }
}
