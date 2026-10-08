package com.moyue.common.monitor.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Druid 连接池监控视图（连接池监视域）。
 *
 * <p>架构说明书要求连接池由 HikariCP 切换为 Druid，本类即为 Druid 统计信息的对外视图。
 *
 * @author moyue
 */
@Data
public class DruidPoolVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据源名称 */
    private String name;
    /** JDBC URL */
    private String url;
    /** 驱动类 */
    private String driverClassName;
    /** 活跃连接数 */
    private int activeCount;
    /** 空闲连接数 */
    private int idleCount;
    /** 总连接数 */
    private int poolingCount;
    /** 最大连接数 */
    private int maxActive;
    /** 连接等待次数 */
    private long notEmptyWaitCount;
    /** 逻辑打开次数 */
    private long connectCount;
    /** 逻辑关闭次数 */
    private long closeCount;
    /** 执行次数 */
    private long executeCount;
    /** 错误次数 */
    private long errorCount;
    /** 事务提交次数 */
    private long commitCount;
    /** 事务回滚次数 */
    private long rollbackCount;
}
