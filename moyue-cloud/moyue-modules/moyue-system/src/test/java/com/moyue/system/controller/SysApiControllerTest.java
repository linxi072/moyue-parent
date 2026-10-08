package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SysApiController 单元测试（无 Spring 上下文，直接构造控制器并注入 OpenAPI 契约）。
 *
 * <p>覆盖 6 个端点的核心契约：分组计数、按 Tag 过滤与鉴权标记、含斜杠的 apiId 详情、
 * 统计聚合、差集提示、原始契约透传，以及 OpenAPI 为空时的安全默认值。
 */
class SysApiControllerTest {

    private static final String ADMIN_PATH = Constants.ADMIN_PATH_PREFIX + "/system/menus";
    private static final String PUBLIC_PATH = "/api/v1/auth/login";

    /** 构造一个含 2 个分组、1 个鉴权接口、1 个公开接口的 OpenAPI 契约 */
    private OpenAPI buildOpenApi() {
        OpenAPI api = new OpenAPI();
        Paths paths = new Paths();

        PathItem adminItem = new PathItem();
        Operation adminOp = new Operation();
        adminOp.setTags(List.of("系统管理", "菜单管理"));
        adminOp.setSummary("查询菜单列表");
        adminItem.setGet(adminOp);

        PathItem publicItem = new PathItem();
        Operation publicOp = new Operation();
        publicOp.setTags(List.of("认证管理"));
        publicOp.setSummary("用户登录");
        publicItem.setPost(publicOp);

        paths.addPathItem(ADMIN_PATH, adminItem);
        paths.addPathItem(PUBLIC_PATH, publicItem);
        api.paths(paths);
        return api;
    }

    @Test
    void groups_shouldCountByTag() {
        SysApiController c = new SysApiController(Optional.of(buildOpenApi()));
        R<List<Map<String, Object>>> r = c.groups();
        assertEquals(0, r.getCode());
        List<Map<String, Object>> groups = r.getData();
        // 系统管理=1, 菜单管理=1, 认证管理=1
        assertEquals(3, groups.size());
        assertTrue(groups.get(0).containsKey("tag"));
        assertTrue(groups.get(0).containsKey("count"));
    }

    @Test
    void byTag_shouldFilterAndMarkAuth() {
        SysApiController c = new SysApiController(Optional.of(buildOpenApi()));
        R<List<Map<String, Object>>> r = c.byTag("系统管理");
        assertEquals(0, r.getCode());
        List<Map<String, Object>> list = r.getData();
        assertEquals(1, list.size());
        Map<String, Object> row = list.get(0);
        assertEquals(ADMIN_PATH, row.get("path"));
        assertEquals("GET", row.get("method"));
        assertEquals("查询菜单列表", row.get("summary"));
        assertEquals(true, row.get("auth")); // 以 /api/v1/admin 开头
    }

    @Test
    void byTag_publicPath_shouldNotRequireAuth() {
        SysApiController c = new SysApiController(Optional.of(buildOpenApi()));
        R<List<Map<String, Object>>> r = c.byTag("认证管理");
        Map<String, Object> row = r.getData().get(0);
        assertEquals(PUBLIC_PATH, row.get("path"));
        assertEquals("POST", row.get("method"));
        assertEquals(false, row.get("auth"));
    }

    @Test
    void detail_withSlashApiId_shouldFind() {
        SysApiController c = new SysApiController(Optional.of(buildOpenApi()));
        // apiId 是完整接口路径，含斜杠 —— 必须由 @RequestParam 承载
        R<Map<String, Object>> r = c.detail(ADMIN_PATH);
        assertEquals(0, r.getCode());
        Map<String, Object> d = r.getData();
        assertEquals(ADMIN_PATH, d.get("apiId"));
        assertEquals(true, d.get("found"));
        assertNotNull(d.get("pathItem"));
    }

    @Test
    void detail_unknownApiId_shouldReportNotFound() {
        SysApiController c = new SysApiController(Optional.of(buildOpenApi()));
        R<Map<String, Object>> r = c.detail("/api/v1/admin/system/unknown");
        assertFalse((Boolean) r.getData().get("found"));
        assertNull(r.getData().get("pathItem"));
    }

    @Test
    void stats_shouldAggregate() {
        SysApiController c = new SysApiController(Optional.of(buildOpenApi()));
        R<Map<String, Object>> r = c.stats();
        Map<String, Object> s = r.getData();
        assertEquals(2, s.get("total"));
        assertEquals(1, s.get("authCount")); // 仅 admin 路径鉴权
        assertEquals(50.0, (Double) s.get("authRatio"), 0.001); // 1/2 = 50%
    }

    @Test
    void diff_shouldReturnBaselineNote() {
        SysApiController c = new SysApiController(Optional.of(buildOpenApi()));
        R<Map<String, Object>> r = c.diff();
        Map<String, Object> d = r.getData();
        assertEquals(2, d.get("currentPaths"));
        assertEquals(0, d.get("baselinePaths"));
        assertNotNull(d.get("note"));
    }

    @Test
    void raw_shouldReturnOpenApi() {
        OpenAPI api = buildOpenApi();
        SysApiController c = new SysApiController(Optional.of(api));
        R<Object> r = c.raw();
        assertSame(api, r.getData());
    }

    @Test
    void emptyOpenApi_shouldReturnSafeDefaults() {
        SysApiController c = new SysApiController(Optional.empty());
        assertEquals(0, c.groups().getData().size());
        assertEquals(0, c.byTag("x").getData().size());
        assertEquals(0, ((Map<?, ?>) c.stats().getData()).get("total"));
    }
}
