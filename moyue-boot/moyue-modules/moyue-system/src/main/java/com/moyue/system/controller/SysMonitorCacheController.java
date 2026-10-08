package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.monitor.collector.RedisCollector;
import com.moyue.common.monitor.model.RedisInfoVO;
import com.moyue.common.security.annotation.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缓存监控（⑬）：7 个端点。数据源为 Redis INFO / DBSIZE，禁止使用 KEYS *。
 *
 * @author moyue
 */
@Slf4j
@Tag(name = "缓存监控", description = "Redis 运行指标、Key 浏览与清理")
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/monitor/cache")
@RequiredArgsConstructor
public class SysMonitorCacheController {

    /** 单页列举上限 */
    private static final int MAX_KEYS = 200;
    /** 大 value 截断长度 */
    private static final int MAX_VALUE_LEN = 2000;

    private final RedisCollector redisCollector;
    private final StringRedisTemplate stringRedisTemplate;

    @Operation(summary = "Redis 基本信息", description = "版本、运行天数、内存、连接数、命中率")
    @RequiresPermissions("system:monitor:cache")
    @GetMapping
    public R<RedisInfoVO> info() {
        return R.ok(redisCollector.collect());
    }

    @Operation(summary = "Key 数量与按 DB 分布", description = "DBSIZE，禁 KEYS *")
    @RequiresPermissions("system:monitor:cache")
    @GetMapping("/keyspace")
    public R<Map<String, Object>> keyspace() {
        RedisInfoVO vo = redisCollector.collect();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dbSize", vo.getDbSize());
        result.put("keyspace", vo.getKeyspace());
        return R.ok(result);
    }

    @Operation(summary = "命令调用统计 Top N")
    @RequiresPermissions("system:monitor:cache")
    @GetMapping("/command-stats")
    public R<Map<String, Object>> commandStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            var factory = stringRedisTemplate.getConnectionFactory();
            if (factory != null) {
                try (RedisConnection connection = factory.getConnection()) {
                    var props = connection.serverCommands().info("commandstats");
                    if (props != null) {
                        props.stringPropertyNames().forEach(k -> stats.put(k, props.getProperty(k)));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("命令统计采集失败：{}", e.getMessage());
        }
        return R.ok(stats);
    }

    @Operation(summary = "按前缀分页列举 key", description = "前缀必填，单页不超过 200")
    @RequiresPermissions("system:monitor:cache")
    @GetMapping("/keys")
    public R<Map<String, Object>> keys(@RequestParam String prefix,
                                       @RequestParam(defaultValue = "100") int limit) {
        if (prefix == null || prefix.isBlank()) {
            return R.ok(Map.of("keys", List.of(), "total", 0));
        }
        int size = Math.min(limit, MAX_KEYS);
        Set<String> all = stringRedisTemplate.keys(prefix + "*");
        List<String> list = all == null ? new ArrayList<>() : new ArrayList<>(all);
        int total = list.size();
        List<String> page = list.stream().sorted().limit(size).toList();
        return R.ok(Map.of("keys", page, "total", total, "truncated", total > size));
    }

    @Operation(summary = "查看指定 key 的类型、TTL 与值", description = "大 value 截断展示")
    @RequiresPermissions("system:monitor:cache")
    @GetMapping("/value")
    public R<Map<String, Object>> value(@RequestParam String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        try {
            var factory = stringRedisTemplate.getConnectionFactory();
            if (factory != null) {
                try (RedisConnection connection = factory.getConnection()) {
                    byte[] raw = key.getBytes(StandardCharsets.UTF_8);
                    DataType type = connection.keyCommands().type(raw);
                    Long ttl = connection.keyCommands().ttl(raw);
                    result.put("type", type == null ? "none" : type.code());
                    result.put("ttlSeconds", ttl == null ? -1 : ttl);
                    byte[] bytes = connection.stringCommands().get(raw);
                    String value = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
                    result.put("value", value.length() > MAX_VALUE_LEN
                            ? value.substring(0, MAX_VALUE_LEN) + "...(truncated)" : value);
                }
            }
        } catch (Exception e) {
            log.warn("读取 key 失败：{}", e.getMessage());
            result.put("error", String.valueOf(e.getMessage()));
        }
        return R.ok(result);
    }

    @Operation(summary = "删除指定 key")
    @RequiresPermissions("system:monitor:cache:remove")
    @Log(title = "缓存监控", businessType = BusinessType.DELETE)
    @DeleteMapping("/keys")
    public R<Boolean> deleteKey(@RequestParam String key) {
        return R.ok(Boolean.TRUE.equals(stringRedisTemplate.delete(key)));
    }

    @Operation(summary = "按前缀批量清理", description = "需二次确认，记录操作日志")
    @RequiresPermissions("system:monitor:cache:remove")
    @Log(title = "缓存监控", businessType = BusinessType.CLEAN)
    @DeleteMapping("/keys/prefix")
    public R<Integer> deleteByPrefix(@RequestParam String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return R.ok(0);
        }
        Set<String> keys = stringRedisTemplate.keys(prefix + "*");
        if (keys == null || keys.isEmpty()) {
            return R.ok(0);
        }
        Long deleted = stringRedisTemplate.delete(keys);
        return R.ok(deleted == null ? 0 : deleted.intValue());
    }
}
