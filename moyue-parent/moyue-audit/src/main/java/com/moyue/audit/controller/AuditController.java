package com.moyue.audit.controller;

import com.moyue.audit.entity.AuditTaskEntity;
import com.moyue.audit.service.AuditService;
import com.moyue.common.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审核管理接口。
 * 路径前缀 /api/v1，完整路径 /api/v1/admin/audit/comments 与网关 /api/v1/admin/audit/** 路由匹配。
 */
@RestController
@RequestMapping("/api/v1")
public class AuditController {

    @Autowired
    private AuditService auditService;

    /** 查询待投递审核任务：GET /api/v1/admin/audit/comments */
    @GetMapping("/admin/audit/comments")
    public R<List<AuditTaskEntity>> listPendingComments() {
        return R.ok(auditService.listPending());
    }
}
