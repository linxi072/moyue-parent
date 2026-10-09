package com.moyue.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.core.result.R;
import com.moyue.system.client.XxlJobAdminClient;
import com.moyue.system.config.XxlJobAdminProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SysJobController 单元测试（无 Spring 上下文，直接构造控制器并注入 Admin 客户端）。
 *
 * <p>覆盖 ⑩ 定时任务域：调度中心连通性（可用 / 不可达）、执行器分组列表（透传 Admin pageList）。
 * 列表 / 启停 / 触发 / 日志等写路径依赖 Admin 会话，由 E2E 与实跑覆盖。
 */
class SysJobControllerTest {

    private static final String ADMIN_ADDRESS = "http://xxl:8080/xxl-job-admin";

    private SysJobController build(XxlJobAdminClient client) {
        XxlJobAdminProperties props = new XxlJobAdminProperties();
        props.setAddress(ADMIN_ADDRESS);
        return new SysJobController(client, props);
    }

    @Test
    void health_available_shouldReportAddress() {
        XxlJobAdminClient client = mock(XxlJobAdminClient.class);
        when(client.health()).thenReturn(true);
        R<Map<String, Object>> r = build(client).health();
        assertEquals(0, r.getCode());
        assertTrue((Boolean) r.getData().get("available"));
        assertEquals(ADMIN_ADDRESS, r.getData().get("address"));
    }

    @Test
    void health_unavailable_shouldReportFalse() {
        XxlJobAdminClient client = mock(XxlJobAdminClient.class);
        when(client.health()).thenReturn(false);
        R<Map<String, Object>> r = build(client).health();
        assertEquals(0, r.getCode());
        assertFalse((Boolean) r.getData().get("available"));
    }

    @Test
    void groups_shouldProxyAdminJobGroupPageList() throws Exception {
        XxlJobAdminClient client = mock(XxlJobAdminClient.class);
        ObjectMapper om = new ObjectMapper();
        JsonNode node = om.readTree(
                "{\"data\":[{\"id\":1,\"appname\":\"moyue-executor\",\"title\":\"墨阅执行器\"}],\"recordsTotal\":1}");
        when(client.post(eq("/jobgroup/pageList"), any())).thenReturn(node);
        R<JsonNode> r = build(client).groups();
        assertEquals(0, r.getCode());
        assertSame(node, r.getData());
    }
}
