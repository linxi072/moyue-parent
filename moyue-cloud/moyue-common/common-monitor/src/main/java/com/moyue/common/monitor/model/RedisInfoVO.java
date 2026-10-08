package com.moyue.common.monitor.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Redis 监控视图（缓存监控域）。
 *
 * @author moyue
 */
@Data
public class RedisInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Redis 版本 */
    private String version;
    /** 运行模式 standalone / cluster */
    private String mode;
    /** 运行时长（秒） */
    private long uptimeSeconds;
    /** 已连接客户端数 */
    private long connectedClients;
    /** 已使用内存，如 12.30M */
    private String usedMemoryHuman;
    /** 内存峰值 */
    private String usedMemoryPeakHuman;
    /** 配置的最大内存，0 表示不限制 */
    private long maxMemory;
    /** 命中次数 */
    private long keyspaceHits;
    /** 未命中次数 */
    private long keyspaceMisses;
    /** 命中率 %，两位小数 */
    private double hitRate;
    /** 累计处理命令数 */
    private long totalCommandsProcessed;
    /** 当前 db 的 key 数量 */
    private long dbSize;
    /** 各 db 的 key 统计，如 db0 -> "keys=12,expires=3" */
    private Map<String, String> keyspace;
    /** RDB 是否正在落盘 */
    private boolean rdbInProgress;
    /** AOF 是否开启 */
    private boolean aofEnabled;
}
