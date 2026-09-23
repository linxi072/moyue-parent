package com.moyue.operation.controller;

import com.moyue.common.core.domain.PageResult;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.operation.entity.AuthorIncomeEntity;
import com.moyue.operation.entity.RewardOrderEntity;
import com.moyue.operation.service.RewardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 打赏接口（读者端）。
 * 完整路径 /api/v1/rewards，经网关 /api/v1/rewards/** 路由到本服务。
 * 打赏人身份一律取网关注入的 X-User-Id，绝不信任请求体。
 */
@RestController
@RequestMapping("/api/v1/rewards")
public class RewardController {

    @Autowired
    private RewardService rewardService;

    /** 创建打赏订单（待支付）：POST /api/v1/rewards */
    @PostMapping
    public R<RewardOrderEntity> create(@RequestBody CreateRewardRequest req, HttpServletRequest request) {
        long userId = requireUserId(request);
        return R.ok(rewardService.createOrder(userId, req.getBookId(), req.getChapterId(),
                req.getAmount(), req.getPayChannel()));
    }

    /** 支付（模拟渠道回调，幂等）：POST /api/v1/rewards/{orderNo}/pay */
    @PostMapping("/{orderNo}/pay")
    public R<RewardOrderEntity> pay(@PathVariable String orderNo, HttpServletRequest request) {
        return R.ok(rewardService.pay(orderNo, requireUserId(request)));
    }

    /** 我的打赏记录：GET /api/v1/rewards */
    @GetMapping
    public R<PageResult<RewardOrderEntity>> mine(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 HttpServletRequest request) {
        return R.ok(rewardService.myOrders(requireUserId(request), page, size));
    }

    /** 我的稿酬流水（作者视角）：GET /api/v1/rewards/income */
    @GetMapping("/income")
    public R<PageResult<AuthorIncomeEntity>> income(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    HttpServletRequest request) {
        return R.ok(rewardService.myIncome(requireUserId(request), page, size));
    }

    /** 稿酬汇总：累计 + 本月（16-24）：GET /api/v1/rewards/income/summary */
    @GetMapping("/income/summary")
    public R<RewardService.IncomeSummary> incomeSummary(HttpServletRequest request) {
        return R.ok(rewardService.incomeSummary(requireUserId(request)));
    }

    /** 订单详情（仅下单人可见）：GET /api/v1/rewards/{orderNo} */
    @GetMapping("/{orderNo}")
    public R<RewardOrderEntity> detail(@PathVariable String orderNo, HttpServletRequest request) {
        return R.ok(rewardService.getOrder(orderNo, requireUserId(request)));
    }

    // ------------------------------ 上下文工具 ------------------------------

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

    // ------------------------------ 请求体 ------------------------------

    /** 创建打赏订单请求 */
    @Data
    public static class CreateRewardRequest {
        private Long bookId;
        private Long chapterId;
        private BigDecimal amount;
        /** 1 微信 / 2 支付宝，默认 1 */
        private Integer payChannel;
    }
}
