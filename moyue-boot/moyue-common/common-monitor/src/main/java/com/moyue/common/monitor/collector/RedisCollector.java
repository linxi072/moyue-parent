package com.moyue.common.monitor.collector;

import com.moyue.common.monitor.model.RedisInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Redis 指标采集（缓存监控域）。
 *
 * <p>通过 {@code INFO} 命令解析，与架构说明书「缓存监控 = Actuator + Redis INFO 自研页面」一致。
 * 注意：禁止使用 {@code KEYS *}，命令统计里若发现该命令应视为风险信号。
 *
 * @author moyue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCollector {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 采集 Redis 指标。
     *
     * @return Redis 监控视图
     */
    public RedisInfoVO collect() {
        RedisInfoVO vo = new RedisInfoVO();
        try {
            RedisConnectionFactory factory = stringRedisTemplate.getConnectionFactory();
            if (factory == null) {
                return vo;
            }
            try (RedisConnection connection = factory.getConnection()) {
                Properties info = connection.serverCommands().info();
                if (info == null || info.isEmpty()) {
                    return vo;
                }
                vo.setVersion(get(info, "redis_version"));
                vo.setMode("cluster".equalsIgnoreCase(get(info, "redis_mode")) ? "cluster" : "standalone");
                vo.setUptimeSeconds(lng(info, "uptime_in_seconds"));
                vo.setConnectedClients(lng(info, "connected_clients"));
                vo.setUsedMemoryHuman(get(info, "used_memory_human"));
                vo.setUsedMemoryPeakHuman(get(info, "used_memory_peak_human"));
                vo.setMaxMemory(lng(info, "maxmemory"));
                vo.setTotalCommandsProcessed(lng(info, "total_commands_processed"));
                vo.setRdbInProgress("1".equals(get(info, "rdb_bgsave_in_progress")));
                vo.setAofEnabled("1".equals(get(info, "aof_enabled")));

                long hits = lng(info, "keyspace_hits");
                long misses = lng(info, "keyspace_misses");
                vo.setKeyspaceHits(hits);
                vo.setKeyspaceMisses(misses);
                vo.setHitRate(hitRate(hits, misses));

                vo.setDbSize(connection.serverCommands().dbSize());
                vo.setKeyspace(parseKeyspace(info));
            }
        } catch (Exception e) {
            log.warn("Redis 指标采集失败：{}", e.getMessage());
        }
        return vo;
    }

    private Map<String, String> parseKeyspace(Properties info) {
        Map<String, String> keyspace = new HashMap<>();
        for (String name : info.stringPropertyNames()) {
            if (name.startsWith("db")) {
                keyspace.put(name, info.getProperty(name));
            }
        }
        return keyspace;
    }

    private double hitRate(long hits, long misses) {
        long total = hits + misses;
        if (total == 0) {
            return 0;
        }
        return BigDecimal.valueOf((double) hits / total * 100)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static String get(Properties info, String key) {
        return info.getProperty(key, "");
    }

    private static long lng(Properties info, String key) {
        String v = get(info, key);
        if (v.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
