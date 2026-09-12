package com.moyue.audit.controller;

import com.moyue.audit.entity.AuditTaskEntity;
import com.moyue.audit.service.AuditService;
import com.moyue.common.Constants;
import com.moyue.common.R;
import jakarta.servlet.http.HttpServletRequest;
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
 * 16-20：裁决接口支持 remark 审核意见，operatorId 取网关注入 X-User-Id 落库。
 * 16-21：待审任务支持按 bizType 过滤（1 章节 / 2 评论）。
 */
@RestController
@RequestMapping("/api/v1")
public class AuditController {

    @Autowired
    private AuditService auditService;

    /**
     * 查询待投递审核任务（兼容旧路径）：GET /api/v1/admin/audit/comments
     * 16-21：bizType 可选过滤；前端按「待审评论」口径调用时传 bizType=2。
     */
    @GetMapping("/admin/audit/comments")
    public R<List<AuditTaskEntity>> listPendingComments(
            @RequestParam(required = false) Integer bizType) {
        return R.ok(auditService.listPending(bizType));
    }

    /** 查询审核任务（可按 status / bizType 过滤）：GET /api/v1/admin/audit/tasks?status=0&bizType=2 */
    @GetMapping("/admin/audit/tasks")
    public R<List<AuditTaskEntity>> listTasks(@RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) Integer bizType) {
        return R.ok(auditService.listTasks(status, bizType));
    }

    /** 审核通过：PUT /api/v1/admin/audit/tasks/{taskId}/approve?remark=... */
    @PutMapping("/admin/audit/tasks/{taskId}/approve")
    public R<AuditTaskEntity> approve(@PathVariable Long taskId,
                                      @RequestParam(required = false) String remark,
                                      HttpServletRequest request) {
        return R.ok(auditService.decide(taskId, true, remark, resolveOperatorId(request)));
    }

    /** 审核驳回：PUT /api/v1/admin/audit/tasks/{taskId}/reject?remark=... */
    @PutMapping("/admin/audit/tasks/{taskId}/reject")
    public R<AuditTaskEntity> reject(@PathVariable Long taskId,
                                     @RequestParam(required = false) String remark,
                                     HttpServletRequest request) {
        return R.ok(auditService.decide(taskId, false, remark, resolveOperatorId(request)));
    }

    // ------------------------------ 上下文工具 ------------------------------

    /** 从网关注入头解析操作人 ID；解析失败返回 null（意见仍落库，操作人留空） */
    private Long resolveOperatorId(HttpServletRequest request) {
        String uid = request.getHeader(Constants.USER_ID_HEADER);
        if (uid == null || uid.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(uid.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
