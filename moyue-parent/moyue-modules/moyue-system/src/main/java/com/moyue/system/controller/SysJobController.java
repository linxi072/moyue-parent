package com.moyue.system.controller;

import com.moyue.common.core.domain.R;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.core.annotation.RequiresPermissions;
import com.moyue.system.annotation.Log;
import com.moyue.system.service.JobProxyService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 定时任务管理接口（真源为 XXL-Job Admin，本接口走 {@link JobProxyService} HTTP 代理）。
 * 完整前缀 /api/v1/admin/system/job，写操作需 system:job:* 权限码并落操作日志。
 */
@RestController
@RequestMapping("/api/v1/admin/system/job")
public class SysJobController {

    @Autowired
    private JobProxyService jobProxyService;

    /** 任务分页：GET /api/v1/admin/system/job/list */
    @GetMapping("/list")
    public R<PageResult<Map<String, Object>>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) Integer jobGroup,
                                                   @RequestParam(required = false) Integer triggerStatus,
                                                   @RequestParam(required = false) String jobDesc,
                                                   @RequestParam(required = false) String executorHandler) {
        return R.ok(jobProxyService.pageJobs(page, size, jobGroup, triggerStatus, jobDesc, executorHandler));
    }

    /** 任务详情：GET /api/v1/admin/system/job/{id} */
    @GetMapping("/{id}")
    public R<Map<String, Object>> get(@PathVariable long id) {
        return R.ok(jobProxyService.loadJob(id));
    }

    /** 新增任务：POST /api/v1/admin/system/job */
    @PostMapping
    @RequiresPermissions("system:job:add")
    @Log(module = "定时任务", businessType = Log.BusinessType.INSERT)
    public R<Void> add(@RequestBody JobReq req) {
        jobProxyService.addJob(toForm(req));
        return R.ok();
    }

    /** 修改任务：PUT /api/v1/admin/system/job */
    @PutMapping
    @RequiresPermissions("system:job:edit")
    @Log(module = "定时任务", businessType = Log.BusinessType.UPDATE)
    public R<Void> update(@RequestBody JobReq req) {
        jobProxyService.updateJob(toForm(req));
        return R.ok();
    }

    /** 删除任务：DELETE /api/v1/admin/system/job/{id} */
    @DeleteMapping("/{id}")
    @RequiresPermissions("system:job:remove")
    @Log(module = "定时任务", businessType = Log.BusinessType.DELETE)
    public R<Void> remove(@PathVariable long id) {
        jobProxyService.removeJob(id);
        return R.ok();
    }

    /** 任务启停（triggerStatus：0 停止 / 1 启动）：PUT /api/v1/admin/system/job/changeStatus */
    @PutMapping("/changeStatus")
    @RequiresPermissions("system:job:changeStatus")
    @Log(module = "定时任务", businessType = Log.BusinessType.UPDATE)
    public R<Void> changeStatus(@RequestBody ChangeStatusReq req) {
        if (req.getTriggerStatus() != null && req.getTriggerStatus() == 1) {
            jobProxyService.startJob(req.getId());
        } else {
            jobProxyService.stopJob(req.getId());
        }
        return R.ok();
    }

    /** 手动执行一次：PUT /api/v1/admin/system/job/run */
    @PutMapping("/run")
    @RequiresPermissions("system:job:run")
    @Log(module = "定时任务", businessType = Log.BusinessType.UPDATE)
    public R<Void> run(@RequestBody RunReq req) {
        jobProxyService.triggerJob(req.getId(), req.getExecutorParam(), req.getAddressList());
        return R.ok();
    }

    /** 调度日志分页：GET /api/v1/admin/system/job/log */
    @GetMapping("/log")
    public R<PageResult<Map<String, Object>>> log(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) Integer jobGroup,
                                                  @RequestParam(required = false) Long jobId,
                                                  @RequestParam(required = false) Integer logStatus,
                                                  @RequestParam(required = false) String filterTime) {
        return R.ok(jobProxyService.pageLogs(page, size, jobGroup, jobId, logStatus, filterTime));
    }

    // ====================== 工具 ======================

    /** 请求体 → XXL-Job Admin 表单参数（Form 表单提交，字段对齐 XXL-Job Admin 2.4.0） */
    private MultiValueMap<String, String> toForm(JobReq req) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (req.getId() != null) {
            form.add("id", String.valueOf(req.getId()));
        }
        form.add("jobGroup", String.valueOf(req.getJobGroup() == null ? 0 : req.getJobGroup()));
        form.add("jobDesc", nullToEmpty(req.getJobDesc()));
        form.add("scheduleType", nullToEmpty(req.getScheduleType()));
        form.add("scheduleConf", nullToEmpty(req.getScheduleConf()));
        form.add("glueType", req.getGlueType() == null ? "BEAN" : req.getGlueType());
        form.add("executorHandler", nullToEmpty(req.getExecutorHandler()));
        form.add("executorParam", nullToEmpty(req.getExecutorParam()));
        form.add("executorRouteStrategy", req.getExecutorRouteStrategy() == null
                ? "FIRST" : req.getExecutorRouteStrategy());
        form.add("misfireStrategy", req.getMisfireStrategy() == null
                ? "DO_NOTHING" : req.getMisfireStrategy());
        form.add("executorBlockStrategy", req.getExecutorBlockStrategy() == null
                ? "SERIAL_EXECUTION" : req.getExecutorBlockStrategy());
        form.add("executorTimeout", String.valueOf(req.getExecutorTimeout() == null ? 0 : req.getExecutorTimeout()));
        form.add("executorFailRetryCount",
                String.valueOf(req.getExecutorFailRetryCount() == null ? 0 : req.getExecutorFailRetryCount()));
        form.add("author", nullToEmpty(req.getAuthor()));
        form.add("alarmEmail", nullToEmpty(req.getAlarmEmail()));
        form.add("triggerStatus", "0");
        return form;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ====================== 请求体 ======================

    /** 任务请求体（字段对齐 XXL-Job Admin 2.4.0 XxlJobInfo） */
    @Data
    public static class JobReq {
        /** 任务 ID（修改时必填） */
        private Long id;
        /** 执行器分组 ID */
        private Integer jobGroup;
        /** 任务描述 */
        private String jobDesc;
        /** 调度类型：CRON / FIX_RATE */
        private String scheduleType;
        /** 调度配置：cron 表达式或固定速率秒数 */
        private String scheduleConf;
        /** 运行模式：BEAN / GLUE_GROOVY 等 */
        private String glueType;
        /** 执行器任务处理器名 */
        private String executorHandler;
        /** 任务参数 */
        private String executorParam;
        /** 路由策略：FIRST / ROUND / FAILOVER 等 */
        private String executorRouteStrategy;
        /** 调度过期策略：DO_NOTHING / FIRE_ONCE_NOW */
        private String misfireStrategy;
        /** 阻塞处理策略：SERIAL_EXECUTION / DISCARD_LATER / COVER_EARLY */
        private String executorBlockStrategy;
        /** 任务超时时间（秒，0 不限制） */
        private Integer executorTimeout;
        /** 失败重试次数 */
        private Integer executorFailRetryCount;
        /** 负责人 */
        private String author;
        /** 报警邮件 */
        private String alarmEmail;
    }

    /** 任务启停请求体 */
    @Data
    public static class ChangeStatusReq {
        private long id;
        /** 0 停止 / 1 启动 */
        private Integer triggerStatus;
    }

    /** 手动执行请求体 */
    @Data
    public static class RunReq {
        private long id;
        private String executorParam;
        private String addressList;
    }
}
