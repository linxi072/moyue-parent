package com.moyue.audit.controller;

import com.moyue.audit.entity.AuditTaskEntity;
import com.moyue.audit.service.AuditService;
import com.moyue.common.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审核管理接口（后台）。
 * 路径均以 /api/v1/admin/audit 开头，匹配网关 /api/v1/admin/audit/** 路由，
 * 并由 AdminRoleInterceptor（moyue-common）做 role=3 断言。
 */
@RestController
@RequestMapping("/api/v1")
public class AuditController {

    @Autowired
    private AuditService auditService;

    /** 查询待投递审核任务（兼容旧路径）：GET /api/v1/admin/audit/comments */
    @GetMapping("/admin/audit/comments")
    public R<List<AuditTaskEntity>> listPendingComments() {
        return R.ok(auditService.listPending());
    }

    /** 查询审核任务（可按 status 过滤）：GET /api/v1/admin/audit/tasks?status=0 */
    @GetMapping("/admin/audit/tasks")
    public R<List<AuditTaskEntity>> listTasks(@RequestParam(required = false) Integer status) {
        return R.ok(auditService.listTasks(status));
    }

    /** 审核通过：PUT /api/v1/admin/audit/tasks/{taskId}/approve */
    @PutMapping("/admin/audit/tasks/{taskId}/approve")
    public R<AuditTaskEntity> approve(@PathVariable Long taskId) {
        return R.ok(auditService.decide(taskId, true));
    }

    /** 审核驳回：PUT /api/v1/admin/audit/tasks/{taskId}/reject */
    @PutMapping("/admin/audit/tasks/{taskId}/reject")
    public R<AuditTaskEntity> reject(@PathVariable Long taskId) {
        return R.ok(auditService.decide(taskId, false));
    }
}
