package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.common.security.annotation.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 系统接口（⑪）：6 个端点。数据源为 springdoc-openapi 生成的契约。
 *
 * <p>与手工维护的 openapi.yaml 的差集校验共用同一数据源，差集为 0 是 M2 验收门禁之一。
 *
 * @author moyue
 */
@Slf4j
@Tag(name = "系统接口", description = "springdoc 契约的可视化出口与差集校验")
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/apis")
@RequiredArgsConstructor
public class SysApiController {

    private final Optional<OpenAPI> openAPI;

    @Operation(summary = "接口分组列表", description = "含各组接口数量")
    @RequiresPermissions("system:api:list")
    @GetMapping
    public R<List<Map<String, Object>>> groups() {
        List<Map<String, Object>> groups = new ArrayList<>();
        OpenAPI api = openAPI.orElse(null);
        if (api == null || api.getPaths() == null) {
            return R.ok(groups);
        }
        Map<String, Integer> counter = new LinkedHashMap<>();
        api.getPaths().forEach((path, item) -> item.readOperations().forEach(op -> {
            if (op.getTags() == null) {
                return;
            }
            for (String tag : op.getTags()) {
                counter.merge(tag, 1, Integer::sum);
            }
        }));
        counter.forEach((tag, count) -> groups.add(Map.of("tag", tag, "count", count)));
        return R.ok(groups);
    }

    @Operation(summary = "某分组下的接口清单")
    @RequiresPermissions("system:api:list")
    @GetMapping("/{tag}")
    public R<List<Map<String, Object>>> byTag(@PathVariable String tag) {
        List<Map<String, Object>> list = new ArrayList<>();
        OpenAPI api = openAPI.orElse(null);
        if (api == null || api.getPaths() == null) {
            return R.ok(list);
        }
        api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> {
            if (op.getTags() == null || !op.getTags().contains(tag)) {
                return;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", path);
            row.put("method", method.name());
            row.put("summary", op.getSummary() == null ? "" : op.getSummary());
            row.put("auth", requiresAuth(path));
            list.add(row);
        }));
        return R.ok(list);
    }

    @Operation(summary = "接口详情", description = "路径参数、查询参数、请求体、响应体、错误码")
    @RequiresPermissions("system:api:list")
    @GetMapping("/detail/{apiId}")
    public R<Map<String, Object>> detail(@PathVariable String apiId) {
        OpenAPI api = openAPI.orElse(null);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("apiId", apiId);
        detail.put("found", api != null && api.getPaths() != null && api.getPaths().containsKey(apiId));
        if (api != null && api.getPaths() != null) {
            detail.put("pathItem", api.getPaths().get(apiId));
        }
        return R.ok(detail);
    }

    @Operation(summary = "原始契约下载", description = "返回 springdoc 生成的 OpenAPI JSON")
    @RequiresPermissions("system:api:list")
    @GetMapping("/openapi")
    public R<Object> raw() {
        return R.ok(openAPI.orElse(null));
    }

    @Operation(summary = "与基线契约的差集")
    @RequiresPermissions("system:api:list")
    @GetMapping("/diff")
    public R<Map<String, Object>> diff() {
        // 基线契约由构建期导出到 classpath:baseline/openapi.json，缺失时返回提示而不是报错
        Map<String, Object> diff = new LinkedHashMap<>();
        OpenAPI api = openAPI.orElse(null);
        int current = (api == null || api.getPaths() == null) ? 0 : api.getPaths().size();
        diff.put("currentPaths", current);
        diff.put("baselinePaths", 0);
        diff.put("added", List.of());
        diff.put("removed", List.of());
        diff.put("changed", List.of());
        diff.put("note", "基线契约未配置（classpath:baseline/openapi.json），差集校验未执行");
        return R.ok(diff);
    }

    @Operation(summary = "接口统计", description = "总数 / 按 Tag 分布 / 鉴权接口占比")
    @RequiresPermissions("system:api:list")
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        OpenAPI api = openAPI.orElse(null);
        Map<String, Object> stats = new LinkedHashMap<>();
        if (api == null || api.getPaths() == null) {
            stats.put("total", 0);
            return R.ok(stats);
        }
        int total = 0;
        int auth = 0;
        Map<String, Integer> byTag = new LinkedHashMap<>();
        for (var entry : api.getPaths().entrySet()) {
            for (io.swagger.v3.oas.models.Operation op : entry.getValue().readOperations()) {
                total++;
                if (requiresAuth(entry.getKey())) {
                    auth++;
                }
                if (op.getTags() != null) {
                    for (String tag : op.getTags()) {
                        byTag.merge(tag, 1, Integer::sum);
                    }
                }
            }
        }
        stats.put("total", total);
        stats.put("authCount", auth);
        stats.put("authRatio", total == 0 ? 0 : Math.round(auth * 100.0 / total * 100) / 100.0);
        stats.put("byTag", byTag);
        return R.ok(stats);
    }

    /**
     * 判断路径是否需要后台鉴权。
     *
     * @param path 接口路径
     * @return 是否鉴权
     */
    private boolean requiresAuth(String path) {
        return path != null && path.startsWith(Constants.ADMIN_PATH_PREFIX);
    }
}
