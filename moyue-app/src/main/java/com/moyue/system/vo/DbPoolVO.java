package com.moyue.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 数据库连接池监控 VO（GET /monitor/dbpool）。
 * 数据源：HikariPoolMXBean（HikariCP 是 Spring Boot 默认连接池）。
 */
@Data
public class DbPoolVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 连接池名称 */
    private String poolName;

    /** 活动连接数 */
    private int activeConnections;

    /** 空闲连接数 */
    private int idleConnections;

    /** 总连接数（活动 + 空闲） */
    private int totalConnections;

    /** 等待获取连接的线程数（>0 说明池偏小或有慢 SQL） */
    private int threadsAwaitingConnection;

    /** 最大池大小 */
    private int maximumPoolSize;

    /** 最小空闲连接数 */
    private int minimumIdle;
}
