package com.moyue.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.system.client.XxlJobAdminClient;
import com.moyue.system.config.XxlJobAdminProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 定时任务（⑩）：8 个端点。无本地表，复用 XXL-Job Admin OpenAPI。
 *
 * <p>边界：不提供新增 / 编辑 / 删除任务，任务由开发声明 {@code @XxlJob} 后由 Admin 注册。
 *
 * @author moyue
 */
@Tag(name = "定时任务", description = "复用 XXL-Job：任务列表、启停、触发、执行日志")
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/jobs")
@RequiredArgsConstructor
public class SysJobController {

    private final XxlJobAdminClient adminClient;
    private final XxlJobAdminProperties adminProperties;

    @Operation(summary = "任务列表", description = "从 Admin OpenAPI 拉取，按执行器分组")
    @RequiresPermissions("system:job:list")
    @GetMapping
    public R<JsonNode> list(@RequestParam(defaultValue = "0") int start,
                            @RequestParam(defaultValue = "10") int length,
                            @RequestParam(required = false) String jobGroup,
                            @RequestParam(required = false) String jobDesc) {
        Map<String, String> params = new HashMap<>();
        params.put("start", String.valueOf(start));
        params.put("length", String.valueOf(length));
        params.put("jobGroup", jobGroup == null ? "" : jobGroup);
        params.put("jobDesc", jobDesc == null ? "" : jobDesc);
        if (!adminProperties.getAppName().isBlank()) {
            params.put("executorHandler", "");
        }
        return R.ok(adminClient.post("/jobinfo/pageList", params));
    }

    @Operation(summary = "任务详情", description = "含 Cron、运行模式、负责人、上次 / 下次执行时间")
    @RequiresPermissions("system:job:query")
    @GetMapping("/{jobId}")
    public R<JsonNode> detail(@PathVariable String jobId) {
        return R.ok(adminClient.post("/jobinfo/pageList",
                Map.of("start", "0", "length", "1", "jobId", jobId)));
    }

    @Operation(summary = "启停任务")
    @RequiresPermissions("system:job:changeStatus")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping("/{jobId}/status")
    public R<JsonNode> status(@PathVariable String jobId, @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "0");
        String path = "1".equals(status) ? "/jobinfo/start" : "/jobinfo/stop";
        return R.ok(adminClient.post(path, Map.of("id", jobId)));
    }

    @Operation(summary = "手动触发一次", description = "可传执行参数")
    @RequiresPermissions("system:job:trigger")
    @Log(title = "定时任务", businessType = BusinessType.OTHER)
    @PostMapping("/{jobId}/trigger")
    public R<JsonNode> trigger(@PathVariable String jobId,
                               @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> params = new HashMap<>();
        params.put("id", jobId);
        params.put("executorParam", body == null ? "" : body.getOrDefault("executorParam", ""));
        return R.ok(adminClient.post("/jobinfo/trigger", params));
    }

    @Operation(summary = "执行日志列表")
    @RequiresPermissions("system:job:query")
    @GetMapping("/{jobId}/logs")
    public R<JsonNode> logs(@PathVariable String jobId,
                            @RequestParam(defaultValue = "0") int start,
                            @RequestParam(defaultValue = "10") int length) {
        return R.ok(adminClient.post("/joblog/pageList", Map.of(
                "jobId", jobId,
                "start", String.valueOf(start),
                "length", String.valueOf(length))));
    }

    @Operation(summary = "单条执行日志详情", description = "含执行备注与耗时")
    @RequiresPermissions("system:job:query")
    @GetMapping("/{jobId}/logs/{logId}/detail")
    public R<JsonNode> logDetail(@PathVariable String jobId, @PathVariable String logId) {
        return R.ok(adminClient.post("/joblog/logDetailCat", Map.of(
                "logId", logId, "fromLineNum", "1")));
    }

    @Operation(summary = "执行器分组列表")
    @RequiresPermissions("system:job:list")
    @GetMapping("/groups")
    public R<JsonNode> groups() {
        return R.ok(adminClient.post("/jobgroup/pageList",
                Map.of("start", "0", "length", "100")));
    }

    @Operation(summary = "调度中心连通性检查", description = "不可达时前端显示只读提示")
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        return R.ok(Map.of(
                "available", adminClient.health(),
                "address", adminProperties.getAddress()));
    }
}
