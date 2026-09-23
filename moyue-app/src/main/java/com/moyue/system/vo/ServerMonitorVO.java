package com.moyue.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 服务器监控 VO：JVM / CPU / 物理内存 / 磁盘 / 线程 快照（GET /monitor/server）。
 * 数据源：OperatingSystemMXBean / MemoryMXBean / ThreadMXBean / File.getTotalSpace。
 */
@Data
public class ServerMonitorVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ====================== JVM ======================

    /** Java 版本，如 17.0.10 */
    private String javaVersion;

    /** Java 安装目录 */
    private String javaHome;

    /** JVM 启动时间（ISO 时间字符串） */
    private String startTime;

    /** 运行时长（毫秒） */
    private long uptimeMs;

    // ====================== OS / CPU ======================

    /** 操作系统名称 */
    private String osName;

    /** 系统架构 */
    private String osArch;

    /** CPU 核数 */
    private int processors;

    /** 进程 CPU 使用率（0~1） */
    private double processCpuLoad;

    /** 系统 CPU使用率（0~1） */
    private double systemCpuLoad;

    // ====================== 物理内存（MB） ======================

    /** 物理内存总量 */
    private long memTotal;

    /** 物理内存可用量 */
    private long memFree;

    /** 物理内存使用率（0~1） */
    private double memUsage;

    // ====================== JVM 堆内存（MB） ======================

    /** 堆初始化大小 */
    private long heapInit;

    /** 堆已使用 */
    private long heapUsed;

    /** 堆已提交 */
    private long heapCommitted;

    /** 堆最大值 */
    private long heapMax;

    /** 堆使用率（0~1） */
    private double heapUsage;

    // ====================== 线程 ======================

    /** 当前线程数 */
    private int threadCount;

    /** 峰值线程数 */
    private int peakThreadCount;

    /** 守护线程数 */
    private int daemonThreadCount;

    /** 死锁线程数 */
    private int deadLockedThreadCount;

    // ====================== 磁盘 ======================

    /** 磁盘分区信息（每根路径一条） */
    private List<DiskInfo> disks;

    /** 磁盘分区信息 */
    @Data
    public static class DiskInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 分区路径，如 C:\ */
        private String path;

        /** 总容量（GB） */
        private double total;

        /** 剩余容量（GB） */
        private double free;

        /** 已用容量（GB） */
        private double used;

        /** 使用率（0~1） */
        private double usage;
    }
}
