package com.moyue.risk.report;

import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 读者端举报接口（需登录，P2-15 R-3）。
 * <p>提交：POST /api/v1/reports（设计稿既定路径，网关路由 moyue-report 已覆盖
 * /api/v1/reports/**；同时别名挂载 /api/v1/report 便于前端按任务口径接入）。</p>
 * <p>我的举报：GET /api/v1/reports/mine。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /** 提交举报（需登录）：POST /api/v1/reports（别名 /api/v1/report） */
    @PostMapping({"/reports", "/report"})
    public R<ReportEntity> submit(@RequestBody ReportCreateRequest req, HttpServletRequest request) {
        long userId = requireUserId(request);
        return R.ok(reportService.submit(userId, req.getTargetType(), req.getTargetId(),
                req.getReasonType(), req.getReason()));
    }

    /** 我的举报（分页）：GET /api/v1/reports/mine?page&size */
    @GetMapping("/reports/mine")
    public R<PageResult<ReportEntity>> mine(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            HttpServletRequest request) {
        long userId = requireUserId(request);
        return R.ok(reportService.listMine(userId, page, size));
    }

    // ------------------------------ 请求体 ------------------------------

    /** 提交举报请求体 */
    public static class ReportCreateRequest {
        private Integer targetType;
        private Long targetId;
        private Integer reasonType;
        private String reason;

        public Integer getTargetType() {
            return targetType;
        }

        public void setTargetType(Integer targetType) {
            this.targetType = targetType;
        }

        public Long getTargetId() {
            return targetId;
        }

        public void setTargetId(Long targetId) {
            this.targetId = targetId;
        }

        public Integer getReasonType() {
            return reasonType;
        }

        public void setReasonType(Integer reasonType) {
            this.reasonType = reasonType;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    // ------------------------------ 上下文工具 ------------------------------

    /** 从网关注入头取当前用户 ID；缺失 / 非法 → 10002 未登录 */
    private long requireUserId(HttpServletRequest request) {
        String uid = request.getHeader(Constants.USER_ID_HEADER);
        if (uid == null || uid.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(uid.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }
}
