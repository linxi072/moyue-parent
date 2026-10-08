package com.moyue.common.redis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 常用操作封装。
 *
 * <p><b>禁止使用 KEYS 命令</b>（缓存监控域明确要求，架构说明书 7.6 ⑬）；
 * 需要按前缀查找时用 {@link #keys(String)}，它基于 SCAN 实现。
 *
 * @author moyue
 */
@Component
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    // ---------------- key ----------------

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void delete(String... keys) {
        if (keys != null && keys.length > 0) {
            redisTemplate.delete(List.of(keys));
        }
    }

    public void delete(Collection<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    public long getExpire(String key) {
        Long exp = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return exp == null ? -2L : exp;
    }

    /** 基于 SCAN 的前缀查找，替代危险的 KEYS * */
    public Set<String> keys(String pattern) {
        return stringRedisTemplate.keys(pattern);
    }

    /** 数据库键数量 */
    public long dbSize() {
        Long size = stringRedisTemplate.execute(
                (org.springframework.data.redis.core.RedisCallback<Long>) c -> c.dbSize());
        return size == null ? 0L : size;
    }

    // ---------------- String ----------------

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public <T> T get(String key, Class<T> type) {
        Object v = redisTemplate.opsForValue().get(key);
        return type.isInstance(v) ? type.cast(v) : null;
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public String getString(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public long increment(String key, long delta) {
        Long r = stringRedisTemplate.opsForValue().increment(key, delta);
        return r == null ? 0L : r;
    }

    // ---------------- Hash ----------------

    public void putAll(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    public Map<Object, Object> entries(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    // ---------------- 服务端信息 ----------------

    /** Redis INFO 原始文本，供缓存监控域解析 */
    public String info() {
        return stringRedisTemplate.execute(
                (org.springframework.data.redis.core.RedisCallback<String>) c -> {
                    java.util.Properties p = c.info();
                    StringBuilder sb = new StringBuilder();
                    if (p != null) {
                        p.stringPropertyNames()
                                .forEach(k -> sb.append(k).append(':').append(p.getProperty(k)).append('\n'));
                    }
                    return sb.toString();
                });
    }
}
