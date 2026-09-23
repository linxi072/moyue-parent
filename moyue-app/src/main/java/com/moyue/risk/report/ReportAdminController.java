package com.moyue.risk.report;

import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.security.RequiresPermissions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报管理接口（后台，P2-15 R-3 闭环）。
 * <p>完整前缀 /api/v1/admin/reports，匹配网关 /api/v1/admin/reports/** 路由，
 * 由 AdminRoleInterceptor（moyue-common）做 role=3 断言 + @RequiresPermissions 细粒度权限码
 * （system:report:*，菜单种子见 V15 迁移）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportAdminController {

    @Autowired
    private ReportService reportService;

    /** 举报列表：GET /api/v1/admin/reports?status&targetType&page&size */
    @GetMapping
    @RequiresPermissions("system:report:list")
    public R<PageResult<ReportEntity>> list(@RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) Integer targetType,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return R.ok(reportService.adminPage(status, targetType, page, size));
    }

    /** 处理举报：PUT /api/v1/admin/reports/{id}/handle，body = {passed, remark} */
    @PutMapping("/{id}/handle")
    @RequiresPermissions("system:report:handle")
    public R<ReportEntity> handle(@PathVariable Long id,
                                  @RequestBody ReportHandleRequest req,
                                  HttpServletRequest request) {
        if (req == null || req.getPassed() == null) {
            return R.fail(com.moyue.common.ResultCode.PARAM_ERROR, "处理结论 passed 不能为空（true 属实 / false 驳回）");
        }
        return R.ok(reportService.handle(id, req.getPassed(), req.getRemark(), resolveOperatorId(request)));
    }

    // ------------------------------ 请求体 ------------------------------

    /** 处理举报请求体：passed=true 属实（章节/评论自动隐藏）/ false 驳回 */
    public static class ReportHandleRequest {
        private Boolean passed;
        private String remark;

        public Boolean getPassed() {
            return passed;
        }

        public void setPassed(Boolean passed) {
            this.passed = passed;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    // ------------------------------ 上下文工具 ------------------------------

    /** 从网关注入头解析操作人 ID；解析失败返回 null（处理意见仍落库，操作人留空） */
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
