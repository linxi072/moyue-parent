package com.moyue.system.controller;

import com.moyue.common.core.result.R;
import com.moyue.common.monitor.collector.ActuatorCollector;
import com.moyue.common.monitor.collector.ServerCollector;
import com.moyue.common.monitor.model.ServerVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.Status;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SysMonitorServerController 单元测试（无 Spring 上下文，直接构造控制器并注入采集器）。
 *
 * <p>覆盖 ⑫ 服务监控域：概览、实例清单（单实例部署返回 local 实例的真实主机 / IP / 健康）、
 * 指定实例详情。Cloud 多实例发现为后续增强（需接入 Nacos NamingService），本测试锁定单实例契约。
 */
class SysMonitorServerControllerTest {

    private SysMonitorServerController build(ServerVO vo, String healthStatus, HealthComponent health) {
        ServerCollector serverCollector = mock(ServerCollector.class);
        ActuatorCollector actuatorCollector = mock(ActuatorCollector.class);
        when(serverCollector.collect()).thenReturn(vo);
        when(actuatorCollector.healthStatus()).thenReturn(healthStatus);
        when(actuatorCollector.healthDetail()).thenReturn(health);
        return new SysMonitorServerController(serverCollector, actuatorCollector);
    }

    private ServerVO sampleVo() {
        ServerVO vo = new ServerVO();
        ServerVO.Sys sys = new ServerVO.Sys();
        sys.setComputerName("app-01");
        sys.setComputerIp("10.0.0.1");
        sys.setOsName("Linux 5.15");
        vo.setSys(sys);
        return vo;
    }

    @Test
    void overview_shouldReturnCollectedVo() {
        ServerVO vo = sampleVo();
        SysMonitorServerController c = build(vo, "UP", mock(HealthComponent.class));
        R<ServerVO> r = c.overview();
        assertEquals(0, r.getCode());
        assertSame(vo, r.getData());
    }

    @Test
    void instances_shouldReturnSingleLocalInstanceWithRealHostAndHealth() {
        ServerVO vo = sampleVo();
        SysMonitorServerController c = build(vo, "UP", mock(HealthComponent.class));
        R<List<Map<String, Object>>> r = c.instances();
        assertEquals(0, r.getCode());
        List<Map<String, Object>> list = r.getData();
        assertEquals(1, list.size());
        Map<String, Object> inst = list.get(0);
        assertEquals("local", inst.get("instanceId"));
        assertEquals("app-01", inst.get("hostName"));
        assertEquals("10.0.0.1", inst.get("ip"));
        assertEquals("UP", inst.get("status"));
        assertEquals("Linux 5.15", inst.get("os"));
    }

    @Test
    void instance_detail_shouldReturnServerAndHealthByInstanceId() {
        ServerVO vo = sampleVo();
        HealthComponent health = mock(HealthComponent.class);
        when(health.getStatus()).thenReturn(new Status("UP"));
        SysMonitorServerController c = build(vo, "UP", health);
        R<Map<String, Object>> r = c.instance("abc");
        assertEquals(0, r.getCode());
        assertEquals("abc", r.getData().get("instanceId"));
        assertSame(vo, r.getData().get("server"));
        assertSame(health, r.getData().get("health"));
        assertNotNull(r.getData().get("server"));
    }
}
