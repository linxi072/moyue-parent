package com.moyue.system.controller;

import com.moyue.common.core.result.R;
import com.moyue.common.monitor.collector.ActuatorCollector;
import com.moyue.common.monitor.collector.DruidCollector;
import com.moyue.common.monitor.model.DruidPoolVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SysMonitorPoolController 单元测试（无 Spring 上下文，直接构造控制器并注入采集器）。
 *
 * <p>覆盖 ⑭ 连接池监视域：概览、数据源清单（多数据源场景，与 collect 同源）、URL 访问统计。
 * 注意 {@code sql() / slowSql() / reset()} 走 DruidStatManagerFacade 静态门面，本测试不覆盖，
 * 避免引入 PowerMock；其采集逻辑由 DruidCollector 单测与实跑覆盖。
 */
class SysMonitorPoolControllerTest {

    private SysMonitorPoolController build(List<DruidPoolVO> pools, Map<String, Object> metrics) {
        DruidCollector druidCollector = mock(DruidCollector.class);
        ActuatorCollector actuatorCollector = mock(ActuatorCollector.class);
        when(druidCollector.collect()).thenReturn(pools);
        when(actuatorCollector.metrics()).thenReturn(metrics);
        return new SysMonitorPoolController(druidCollector, actuatorCollector);
    }

    private DruidPoolVO pool(String name, int active, int max) {
        DruidPoolVO vo = new DruidPoolVO();
        vo.setName(name);
        vo.setActiveCount(active);
        vo.setMaxActive(max);
        return vo;
    }

    @Test
    void overview_shouldReturnCollectedPools() {
        SysMonitorPoolController c = build(List.of(pool("master", 2, 20)), Map.of());
        R<List<DruidPoolVO>> r = c.overview();
        assertEquals(0, r.getCode());
        assertEquals(1, r.getData().size());
        assertEquals("master", r.getData().get(0).getName());
    }

    @Test
    void datasources_shouldReturnSameCollectedPools() {
        SysMonitorPoolController c = build(List.of(pool("slave", 1, 10)), Map.of());
        R<List<DruidPoolVO>> r = c.datasources();
        assertEquals(0, r.getCode());
        assertEquals(1, r.getData().size());
        assertEquals("slave", r.getData().get(0).getName());
    }

    @Test
    void url_stats_shouldWrapActuatorHttpMetrics() {
        Map<String, Object> metrics = Map.of("http.server.requests", Map.of("count", 5));
        SysMonitorPoolController c = build(List.of(), metrics);
        R<Map<String, Object>> r = c.url();
        assertEquals(0, r.getCode());
        assertEquals("actuator:http.server.requests", r.getData().get("source"));
        assertNotNull(r.getData().get("measurements"));
    }
}
