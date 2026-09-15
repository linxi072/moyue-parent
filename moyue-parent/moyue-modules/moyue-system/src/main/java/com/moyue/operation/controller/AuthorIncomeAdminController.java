package com.moyue.operation.controller;

import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.operation.entity.AuthorIncomeEntity;
import com.moyue.operation.service.RewardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 管理端稿酬录入接口（买断分成等无真实付费域的流水由运营手工录入）。
 * 路径 /api/v1/admin/author-incomes 已被 AdminRoleInterceptor 保护（仅 role=3）。
 */
@RestController
@RequestMapping("/api/v1/admin/author-incomes")
public class AuthorIncomeAdminController {

    @Autowired
    private RewardService rewardService;

    /**
     * 录入买断稿酬流水：POST /api/v1/admin/author-incomes
     * 操作人取网关注入的 X-User-Id（管理员），用于鉴权与审计。
     */
    @PostMapping
    public R<AuthorIncomeEntity> recordBuyout(@RequestBody RecordBuyoutRequest req, HttpServletRequest request) {
        // 鉴权：确保是已登录管理员（/api/v1/admin/** 另由 AdminRoleInterceptor 校验 role=3）
        requireUserId(request);
        if (req.getAuthorId() == null || req.getAmount() == null || req.getPeriod() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "作者ID / 金额 / 结算月份均必填");
        }
        AuthorIncomeEntity income = rewardService.recordBuyoutIncome(req.getAuthorId(), req.getBookId(), req.getAmount(), req.getPeriod());
        return R.ok(income);
    }

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

    @Data
    public static class RecordBuyoutRequest {
        private Long authorId;
        private Long bookId;
        private BigDecimal amount;
        private String period;
    }
}
