package com.moyue.system.service;

import com.moyue.system.vo.DbPoolVO;
import com.moyue.system.vo.ServerMonitorVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务监控业务（GET /monitor/server、/monitor/dbpool、/monitor/sql）。
 *
 * <p>数据源：</p>
 * <ul>
 *   <li>CPU / 物理内存：com.sun.management.OperatingSystemMXBean（Java 17：getCpuLoad 等）；</li>
 *   <li>堆内存：MemoryMXBean；磁盘：File.getTotalSpace/getFreeSpace；线程：ThreadMXBean；</li>
 *   <li>连接池：HikariPoolMXBean（DataSource instanceof HikariDataSource）；</li>
 *   <li>慢 SQL：information_schema.processlist（在途查询）+
 *       performance_schema.events_statements_summary_by_digest（TopN 按总耗时）。</li>
 * </ul>
 * <p>任何单项采集失败只降级（字段留空 / warn），不影响其它指标。</p>
 */
@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    /** 慢 SQL TopN 条数 */
    private static final int SQL_TOP_N = 10;

    @Autowired
    private DataSource dataSource;

    // ====================== 服务器监控 ======================

    /** JVM / OS / CPU / 内存 / 磁盘 / 线程 快照 */
    public ServerMonitorVO server() {
        ServerMonitorVO vo = new ServerMonitorVO();

        // JVM 基本信息
        vo.setJavaVersion(System.getProperty("java.version"));
        vo.setJavaHome(System.getProperty("java.home"));
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        vo.setUptimeMs(uptimeMs);
        vo.setStartTime(LocalDateTime.now().minus(Duration.ofMillis(uptimeMs))
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // OS / CPU（com.sun.management 扩展）
        try {
            java.lang.management.OperatingSystemMXBean os =
                    ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                vo.setProcessCpuLoad(safeLoad(sunOs.getProcessCpuLoad()));
                vo.setSystemCpuLoad(safeLoad(sunOs.getCpuLoad()));
                vo.setMemTotal(sunOs.getTotalMemorySize() / 1024 / 1024);
                vo.setMemFree(sunOs.getFreeMemorySize() / 1024 / 1024);
                vo.setMemUsage(sunOs.getTotalMemorySize() <= 0 ? 0
                        : 1.0 - (double) sunOs.getFreeMemorySize() / sunOs.getTotalMemorySize());
            }
            vo.setOsName(os.getName());
            vo.setOsArch(os.getArch());
            vo.setProcessors(os.getAvailableProcessors());
        } catch (Exception e) {
            log.warn("[monitor] 采集 OS/CPU 指标失败：{}", e.getMessage());
        }

        // JVM 堆内存
        try {
            MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
            vo.setHeapInit(memory.getHeapMemoryUsage().getInit() / 1024 / 1024);
            vo.setHeapUsed(memory.getHeapMemoryUsage().getUsed() / 1024 / 1024);
            vo.setHeapCommitted(memory.getHeapMemoryUsage().getCommitted() / 1024 / 1024);
            vo.setHeapMax(memory.getHeapMemoryUsage().getMax() / 1024 / 1024);
            vo.setHeapUsage(memory.getHeapMemoryUsage().getMax() <= 0 ? 0
                    : (double) memory.getHeapMemoryUsage().getUsed() / memory.getHeapMemoryUsage().getMax());
        } catch (Exception e) {
            log.warn("[monitor] 采集堆内存指标失败：{}", e.getMessage());
        }

        // 线程
        try {
            ThreadMXBean thread = ManagementFactory.getThreadMXBean();
            vo.setThreadCount(thread.getThreadCount());
            vo.setPeakThreadCount(thread.getPeakThreadCount());
            vo.setDaemonThreadCount(thread.getDaemonThreadCount());
            long[] deadLocked = thread.findDeadlockedThreads();
            vo.setDeadLockedThreadCount(deadLocked == null ? 0 : deadLocked.length);
        } catch (Exception e) {
            log.warn("[monitor] 采集线程指标失败：{}", e.getMessage());
        }

        // 磁盘（每根路径一条）
        List<ServerMonitorVO.DiskInfo> disks = new ArrayList<>();
        for (File root : File.listRoots()) {
            try {
                ServerMonitorVO.DiskInfo disk = new ServerMonitorVO.DiskInfo();
                disk.setPath(root.getPath());
                double total = root.getTotalSpace() / 1024.0 / 1024 / 1024;
                double free = root.getFreeSpace() / 1024.0 / 1024 / 1024;
                disk.setTotal(round2(total));
                disk.setFree(round2(free));
                disk.setUsed(round2(total - free));
                disk.setUsage(total <= 0 ? 0 : round2((total - free) / total));
                disks.add(disk);
            } catch (Exception e) {
                log.warn("[monitor] 采集磁盘指标失败：{}", root, e);
            }
        }
        vo.setDisks(disks);
        return vo;
    }

    // ====================== 连接池监控 ======================

    /** HikariCP 连接池状态（非 Hikari 数据源时返回降级信息） */
    public DbPoolVO dbPool() {
        DbPoolVO vo = new DbPoolVO();
        try {
            Object pool = unwrapHikariPool();
            if (pool == null) {
                vo.setPoolName("unknown（非 HikariDataSource，无法读取池指标）");
                return vo;
            }
            vo.setPoolName(invokeString(pool, "getPoolName"));
            vo.setActiveConnections(invokeInt(pool, "getActiveConnections"));
            vo.setIdleConnections(invokeInt(pool, "getIdleConnections"));
            vo.setTotalConnections(invokeInt(pool, "getTotalConnections"));
            vo.setThreadsAwaitingConnection(invokeInt(pool, "getThreadsAwaitingConnection"));
            // 最大/最小池配置从 HikariConfig 读取（HikariPoolMXBean 只暴露运行时状态）
            vo.setMaximumPoolSize(readPoolConfigInt(pool, "MaximumPoolSize"));
            vo.setMinimumIdle(readPoolConfigInt(pool, "MinimumIdle"));
        } catch (Exception e) {
            log.warn("[monitor] 采集连接池指标失败：{}", e.getMessage());
            vo.setPoolName("unknown（采集失败：" + e.getMessage() + "）");
        }
        return vo;
    }

    // ====================== 慢 SQL ======================

    /** 慢 SQL 概览：在途查询（processlist）+ 历史 TopN（events_statements_summary_by_digest） */
    public Map<String, Object> slowSql() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processlist", queryProcesslist());
        result.put("digestTop", queryDigestTop());
        return result;
    }

    /** 当前在途查询 TopN（按 TIME 降序） */
    private List<Map<String, Object>> queryProcesslist() {
        String sql = "SELECT ID, USER, HOST, DB, COMMAND, TIME, STATE, LEFT(INFO, 200) AS INFO "
                + "FROM information_schema.processlist "
                + "WHERE COMMAND <> 'Sleep' ORDER BY TIME DESC LIMIT " + SQL_TOP_N;
        return queryForList(sql, "processlist");
    }

    /** 历史 SQL 按总耗时 TopN（performance_schema digest 表） */
    private List<Map<String, Object>> queryDigestTop() {
        String sql = "SELECT DIGEST_TEXT, COUNT_STAR, "
                + "ROUND(SUM_TIMER_WAIT/1000000000000, 3) AS SUM_WAIT_SEC, "
                + "ROUND(AVG_TIMER_WAIT/1000000000, 3) AS AVG_WAIT_MS, "
                + "SUM_ROWS_EXAMINED, SUM_ROWS_SENT "
                + "FROM performance_schema.events_statements_summary_by_digest "
                + "WHERE SCHEMA_NAME = DATABASE() "
                + "ORDER BY SUM_TIMER_WAIT DESC LIMIT " + SQL_TOP_N;
        return queryForList(sql, "digestTop");
    }

    /** 通用查询：失败返回空列表并 warn（performance_schema 可能未开启） */
    private List<Map<String, Object>> queryForList(String sql, String label) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int columnCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            log.warn("[monitor] 查询 {} 失败（performance_schema 可能未开启）：{}", label, e.getMessage());
        }
        return rows;
    }

    // ====================== 反射工具（Hikari 内部类不直接 import，避免类加载顺序耦合） ======================

    /**
     * 从 DataSource 取 HikariPool 实例。
     * Spring Boot 3.2 中 DataSource 可能为代理（routing/lazy），先 instanceof 再走 unwrap 链。
     */
    private Object unwrapHikariPool() throws Exception {
        try {
            Class<?> hikariDsClass = Class.forName("com.zaxxer.hikari.HikariDataSource");
            Object hikariDataSource = null;
            if (hikariDsClass.isInstance(dataSource)) {
                hikariDataSource = dataSource;
            } else if (dataSource.isWrapperFor(hikariDsClass)) {
                hikariDataSource = dataSource.unwrap(hikariDsClass);
            }
            if (hikariDataSource == null) {
                return null;
            }
            Method getPool = hikariDataSource.getClass().getMethod("getHikariPool");
            getPool.setAccessible(true);
            return getPool.invoke(hikariDataSource);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** 从 HikariConfig（pool.getConfig()）读 int 配置项 */
    private int readPoolConfigInt(Object pool, String property) {
        try {
            Object config = pool.getClass().getMethod("getConfig").invoke(pool);
            String getter = "get" + property;
            for (Class<?> c = config.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Object value = c.getMethod(getter).invoke(config);
                    return value instanceof Number n ? n.intValue() : 0;
                } catch (NoSuchMethodException ignored) {
                    // 继续向上找
                }
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String invokeString(Object target, String method) throws Exception {
        Object v = target.getClass().getMethod(method).invoke(target);
        return v == null ? null : String.valueOf(v);
    }

    private int invokeInt(Object target, String method) throws Exception {
        Object v = target.getClass().getMethod(method).invoke(target);
        return v instanceof Number n ? n.intValue() : 0;
    }

    // ====================== 工具 ======================

    /** CPU 负载 -1（未知）转 0，避免前端画图异常 */
    private double safeLoad(double load) {
        return load < 0 ? 0 : round2(load);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
