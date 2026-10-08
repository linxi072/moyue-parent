package com.moyue.common.monitor.collector;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.stat.DataSourceMonitorable;
import com.alibaba.druid.stat.DruidDataSourceStatManager;
import com.alibaba.druid.stat.DruidStatManagerFacade;
import com.moyue.common.monitor.model.DruidPoolVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Druid 连接池指标采集（连接池监视域）。
 *
 * <p>主路径使用 {@code DruidStatManagerFacade#getDataSourceStatDataList()}——这是 Druid 官方
 * 监控页面使用的统计快照，字段齐全且不依赖具体数据源实现类；
 * 快照为空时回退到 {@code DruidDataSourceStatManager} 原生对象，尽量补出等待次数等指标。
 *
 * <p>架构说明书 ADR 要求连接池由 HikariCP 切换为 Druid，本类即为该决策的监控落地。
 *
 * @author moyue
 */
@Slf4j
@Component
public class DruidCollector {

    /**
     * 采集全部数据源的连接池指标。
     *
     * @return 连接池视图列表
     */
    public List<DruidPoolVO> collect() {
        List<DruidPoolVO> list = new ArrayList<>();
        try {
            List<Map<String, Object>> statList = DruidStatManagerFacade.getInstance().getDataSourceStatDataList();
            if (statList != null) {
                for (Map<String, Object> m : statList) {
                    list.add(fromStatMap(m));
                }
            }
        } catch (Exception e) {
            log.warn("Druid 统计快照采集失败：{}", e.getMessage());
        }
        if (list.isEmpty()) {
            collectFromDataSourceInstances(list);
        }
        return list;
    }

    private void collectFromDataSourceInstances(List<DruidPoolVO> list) {
        try {
            Set<DataSourceMonitorable> instances = DruidDataSourceStatManager.getDruidDataSourceInstances();
            if (instances == null) {
                return;
            }
            for (DataSourceMonitorable monitorable : instances) {
                if (monitorable instanceof DruidDataSource ds) {
                    list.add(fromDataSource(ds));
                }
            }
        } catch (Exception e) {
            log.warn("Druid 数据源实例采集失败：{}", e.getMessage());
        }
    }

    private DruidPoolVO fromDataSource(DruidDataSource ds) {
        DruidPoolVO vo = new DruidPoolVO();
        vo.setName(ds.getName() == null ? ds.getUrl() : ds.getName());
        vo.setUrl(ds.getUrl());
        vo.setDriverClassName(ds.getDriverClassName());
        vo.setActiveCount(ds.getActiveCount());
        vo.setPoolingCount(ds.getPoolingCount());
        vo.setIdleCount(Math.max(0, ds.getPoolingCount() - ds.getActiveCount()));
        vo.setMaxActive(ds.getMaxActive());
        vo.setNotEmptyWaitCount(ds.getNotEmptyWaitCount());
        vo.setConnectCount(ds.getConnectCount());
        vo.setCloseCount(ds.getCloseCount());
        vo.setExecuteCount(ds.getExecuteCount());
        vo.setErrorCount(ds.getErrorCount());
        vo.setCommitCount(ds.getCommitCount());
        vo.setRollbackCount(ds.getRollbackCount());
        return vo;
    }

    /**
     * 从 Druid 统计快照 Map 组装视图。
     *
     * <p>快照键名随 Druid 版本可能微调，故取值一律走 {@link #anyLong} / {@link #anyString} 兜底，
     * 缺字段时填 0 / 空串，不抛异常。
     */
    private DruidPoolVO fromStatMap(Map<String, Object> m) {
        DruidPoolVO vo = new DruidPoolVO();
        vo.setName(anyString(m, "Name", "Identity", "URL"));
        vo.setUrl(anyString(m, "Url", "URL", "JdbcUrl"));
        vo.setDriverClassName(anyString(m, "DriverClassName", "DriverClass"));
        vo.setActiveCount((int) anyLong(m, "ActiveCount", "Active"));
        vo.setPoolingCount((int) anyLong(m, "PoolingCount", "Pooling"));
        vo.setIdleCount(Math.max(0, vo.getPoolingCount() - vo.getActiveCount()));
        vo.setMaxActive((int) anyLong(m, "MaxActive", "MaxActiveSize"));
        vo.setNotEmptyWaitCount(anyLong(m, "NotEmptyWaitCount", "NotEmptyWait"));
        vo.setConnectCount(anyLong(m, "ConnectCount"));
        vo.setCloseCount(anyLong(m, "CloseCount"));
        vo.setExecuteCount(anyLong(m, "ExecuteCount"));
        vo.setErrorCount(anyLong(m, "ErrorCount"));
        vo.setCommitCount(anyLong(m, "CommitCount"));
        vo.setRollbackCount(anyLong(m, "RollbackCount"));
        return vo;
    }

    private static String anyString(Map<String, Object> m, String... keys) {
        for (String key : keys) {
            Object v = m.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return "";
    }

    private static long anyLong(Map<String, Object> m, String... keys) {
        for (String key : keys) {
            Object v = m.get(key);
            if (v instanceof Number n) {
                return n.longValue();
            }
            if (v != null) {
                try {
                    return Long.parseLong(String.valueOf(v).trim());
                } catch (NumberFormatException ignored) {
                    // 继续尝试下一个候选键
                }
            }
        }
        return 0L;
    }
}
