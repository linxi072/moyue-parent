package com.moyue.common.monitor.collector;

import com.moyue.common.monitor.model.ServerVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 服务器指标采集。
 *
 * <p>通过 JDK 自带的 {@code OperatingSystemMXBean} + {@code File} 采集，
 * 不引入 oshi 等第三方依赖，保持 common-monitor 轻量。
 *
 * @author moyue
 */
@Slf4j
@Component
public class ServerCollector {

    private static final int MB = 1024 * 1024;
    private static final long GB = 1024L * 1024L * 1024L;

    /** JVM 启动时间戳（毫秒） */
    private final long jvmStartMillis = ManagementFactory.getRuntimeMXBean().getStartTime();

    /**
     * 采集全部服务器指标。
     *
     * @return 服务器监控视图
     */
    public ServerVO collect() {
        ServerVO vo = new ServerVO();
        vo.setCpu(collectCpu());
        vo.setMem(collectMem());
        vo.setJvm(collectJvm());
        vo.setSys(collectSys());
        vo.setDisks(collectDisks());
        return vo;
    }

    private ServerVO.Cpu collectCpu() {
        ServerVO.Cpu cpu = new ServerVO.Cpu();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        cpu.setCpuNum(os.getAvailableProcessors());
        double load = os.getSystemLoadAverage();
        // JDK 标准 API 不直接提供 CPU 时间片，用负载均值折算为近似使用率
        if (load >= 0) {
            double total = Math.min(100.0, round(load / cpu.getCpuNum() * 100.0));
            cpu.setTotal(total);
            cpu.setSys(round(total * 0.3));
            cpu.setUsed(round(total * 0.7));
            cpu.setFree(round(100.0 - total));
        } else {
            cpu.setTotal(0);
            cpu.setSys(0);
            cpu.setUsed(0);
            cpu.setFree(100);
        }
        cpu.setWait(0);
        return cpu;
    }

    private ServerVO.Mem collectMem() {
        ServerVO.Mem mem = new ServerVO.Mem();
        Runtime runtime = Runtime.getRuntime();
        // 注意：这里反映的是 JVM 堆的容器视角，物理机内存需由 Actuator/OS 指标补齐
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long max = runtime.maxMemory();
        mem.setTotal(div(max, GB, 2));
        mem.setFree(div(max - (total - free), GB, 2));
        mem.setUsed(div(total - free, GB, 2));
        mem.setUsage(max <= 0 ? 0 : round((double) (total - free) / max * 100));
        return mem;
    }

    private ServerVO.Jvm collectJvm() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Runtime r = Runtime.getRuntime();
        ServerVO.Jvm jvm = new ServerVO.Jvm();
        jvm.setName(runtime.getVmName());
        Properties props = System.getProperties();
        jvm.setVersion(props.getProperty("java.version") + " (" + props.getProperty("java.vendor") + ")");
        jvm.setHome(props.getProperty("java.home"));
        jvm.setStartTime(LocalDateTime.now()
                .minus(System.currentTimeMillis() - jvmStartMillis, ChronoUnit.MILLIS)
                .withNano(0)
                .toString()
                .replace('T', ' '));
        jvm.setRunTime(formatUptime(System.currentTimeMillis() - jvmStartMillis));
        jvm.setTotal(div(r.totalMemory(), MB, 2));
        jvm.setUsed(div(r.totalMemory() - r.freeMemory(), MB, 2));
        jvm.setFree(div(r.freeMemory(), MB, 2));
        jvm.setUsage(r.totalMemory() <= 0 ? 0 : round((double) (r.totalMemory() - r.freeMemory()) / r.totalMemory() * 100));
        jvm.setInputArgs(String.join(" ", runtime.getInputArguments()));
        return jvm;
    }

    private ServerVO.Sys collectSys() {
        ServerVO.Sys sys = new ServerVO.Sys();
        Properties props = System.getProperties();
        sys.setOsName(props.getProperty("os.name") + " " + props.getProperty("os.version"));
        sys.setOsArch(props.getProperty("os.arch"));
        sys.setUserDir(props.getProperty("user.dir"));
        sys.setComputerName(getHostName());
        sys.setComputerIp(resolveIp());
        return sys;
    }

    private List<ServerVO.Disk> collectDisks() {
        List<ServerVO.Disk> disks = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots == null) {
            return disks;
        }
        for (File root : roots) {
            long total = root.getTotalSpace();
            long free = root.getFreeSpace();
            if (total <= 0) {
                continue;
            }
            ServerVO.Disk disk = new ServerVO.Disk();
            disk.setDirName(root.getPath());
            disk.setSysTypeName(System.getProperty("os.name"));
            disk.setTypeName("本地磁盘");
            disk.setTotal(formatSize(total));
            disk.setFree(formatSize(free));
            disk.setUsed(formatSize(total - free));
            disk.setUsage(round((double) (total - free) / total * 100));
            disks.add(disk);
        }
        return disks;
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String resolveIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static double div(long dividend, long divisor, int scale) {
        if (divisor == 0) {
            return 0;
        }
        return BigDecimal.valueOf((double) dividend / divisor)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String formatSize(long bytes) {
        if (bytes >= GB) {
            return div(bytes, GB, 2) + " GB";
        }
        return div(bytes, MB, 2) + " MB";
    }

    private static String formatUptime(long millis) {
        long days = millis / 86_400_000L;
        long hours = (millis % 86_400_000L) / 3_600_000L;
        long minutes = (millis % 3_600_000L) / 60_000L;
        return days + "天" + hours + "小时" + minutes + "分钟";
    }
}
