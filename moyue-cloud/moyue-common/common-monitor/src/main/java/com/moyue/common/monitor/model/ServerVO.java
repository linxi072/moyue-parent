package com.moyue.common.monitor.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 服务器监控视图（服务监控域）。
 *
 * @author moyue
 */
@Data
public class ServerVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** CPU 信息 */
    private Cpu cpu;
    /** 内存信息 */
    private Mem mem;
    /** JVM 信息 */
    private Jvm jvm;
    /** 系统信息 */
    private Sys sys;
    /** 磁盘列表 */
    private List<Disk> disks;

    @Data
    public static class Cpu implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 核心数 */
        private int cpuNum;
        /** CPU 总使用率 % */
        private double total;
        /** 系统使用率 % */
        private double sys;
        /** 用户使用率 % */
        private double used;
        /** 等待率 % */
        private double wait;
        /** 空闲率 % */
        private double free;
    }

    @Data
    public static class Mem implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 总内存 GB */
        private double total;
        /** 已用 GB */
        private double used;
        /** 剩余 GB */
        private double free;
        /** 使用率 % */
        private double usage;
    }

    @Data
    public static class Jvm implements Serializable {
        private static final long serialVersionUID = 1L;
        /** JVM 名称 */
        private String name;
        /** Java 版本 */
        private String version;
        /** 启动时间 */
        private String startTime;
        /** 运行时长，如 3天2小时 */
        private String runTime;
        /** 安装路径 */
        private String home;
        /** 总内存 MB */
        private double total;
        /** 已用 MB */
        private double used;
        /** 剩余 MB */
        private double free;
        /** 使用率 % */
        private double usage;
        /** 输入参数 */
        private String inputArgs;
    }

    @Data
    public static class Sys implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 服务器名称 */
        private String computerName;
        /** 操作系统 */
        private String osName;
        /** 系统架构 */
        private String osArch;
        /** 服务器 IP */
        private String computerIp;
        /** 项目路径 */
        private String userDir;
    }

    @Data
    public static class Disk implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 盘符路径 */
        private String dirName;
        /** 盘符类型 */
        private String sysTypeName;
        /** 文件类型 */
        private String typeName;
        /** 总大小 */
        private String total;
        /** 剩余大小 */
        private String free;
        /** 已用大小 */
        private String used;
        /** 使用率 % */
        private double usage;
    }
}
