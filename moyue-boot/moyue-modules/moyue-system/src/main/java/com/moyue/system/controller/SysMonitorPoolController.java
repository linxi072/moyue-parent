package com.moyue.system.controller;

import com.alibaba.druid.stat.DruidStatManagerFacade;
import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.monitor.collector.ActuatorCollector;
import com.moyue.common.monitor.collector.DruidCollector;
import com.moyue.common.monitor.model.DruidPoolVO;
import com.moyue.common.security.annotation.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 连接池监视（⑭）：6 个端点。数据源为 Druid StatFilter（ADR-14）。
 *
 * @author moyue
 */
@Slf4j
@Tag(name = "连接池监视", description = "Druid 连接池、SQL 监控与 URL 统计")
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/monitor/pool")
@RequiredArgsConstructor
public class SysMonitorPoolController {

    private final DruidCollector druidCollector;
    private final ActuatorCollector actuatorCollector;

    @Operation(summary = "连接池概览", description = "活跃 / 空闲 / 最大连接数、等待次数、逻辑开关次数")
    @RequiresPermissions("system:monitor:pool")
    @GetMapping
    public R<List<DruidPoolVO>> overview() {
        return R.ok(druidCollector.collect());
    }

    @Operation(summary = "数据源清单", description = "多数据源场景")
    @RequiresPermissions("system:monitor:pool")
    @GetMapping("/datasources")
    public R<List<DruidPoolVO>> datasources() {
        return R.ok(druidCollector.collect());
    }

    @Operation(summary = "SQL 监控列表", description = "执行次数、总耗时、最慢耗时、错误数、慢 SQL 标记")
    @RequiresPermissions("system:monitor:pool")
    @GetMapping("/sql")
    public R<List<Map<String, Object>>> sql() {
        try {
            return R.ok(DruidStatManagerFacade.getInstance().getSqlStatDataList((Integer) null));
        } catch (Exception e) {
            log.warn("SQL 监控采集失败：{}", e.getMessage());
            return R.ok(List.of());
        }
    }

    @Operation(summary = "慢 SQL 列表", description = "超过阈值，阈值可配（moyue.druid.slow-sql-millis）")
    @RequiresPermissions("system:monitor:pool")
    @GetMapping("/sql/slow")
    public R<List<Map<String, Object>>> slowSql(@RequestParam(defaultValue = "1000") long millis) {
        try {
            List<Map<String, Object>> all = DruidStatManagerFacade.getInstance().getSqlStatDataList((Integer) null);
            List<Map<String, Object>> slow = all.stream()
                    .filter(m -> toLong(m.get("MaxTimespan")) > millis
                            || toLong(m.get("MaxTimespanOccurCount")) > 0
                            || toLong(m.get("ErrorCount")) > 0)
                    .toList();
            return R.ok(slow);
        } catch (Exception e) {
            log.warn("慢 SQL 采集失败：{}", e.getMessage());
            return R.ok(List.of());
        }
    }

    @Operation(summary = "URL 访问统计", description = "请求数 / 耗时，取自 Actuator 的 http.server.requests")
    @RequiresPermissions("system:monitor:pool")
    @GetMapping("/url")
    public R<Map<String, Object>> url() {
        Map<String, Object> all = actuatorCollector.metrics();
        Object http = all.get("http.server.requests");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "actuator:http.server.requests");
        result.put("measurements", http == null ? Map.of() : http);
        return R.ok(result);
    }

    @Operation(summary = "重置统计计数器")
    @RequiresPermissions("system:monitor:pool:reset")
    @Log(title = "连接池监视", businessType = BusinessType.CLEAN)
    @PostMapping("/sql/reset")
    public R<Boolean> reset() {
        try {
            DruidStatManagerFacade.getInstance().resetAll();
            return R.ok(true);
        } catch (Exception e) {
            log.warn("重置统计失败：{}", e.getMessage());
            return R.ok(false);
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
