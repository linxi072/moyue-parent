package com.moyue.commerce.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.security.context.UserContext;
import com.moyue.commerce.domain.dto.query.PointsLogQuery;
import com.moyue.commerce.domain.vo.PointsLogVO;
import com.moyue.commerce.service.PointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 积分钱包（C 端用户）。
 *
 * <p>身份取自 {@link UserContext}（网关注入头派生），所有查询与签到操作归属当前登录用户，
 * 不接受前端传入 userId（与站内信收件箱等 C 端接口一致的架构约定）。
 * 网关路由 {@code /api/v1/points/**} 已指向本服务，无需新增路由。
 *
 * @author moyue
 */
@Tag(name = "积分钱包", description = "我的积分余额、积分明细、每日签到")
@Validated
@RestController
@RequestMapping(Constants.API_PREFIX + "/points")
@RequiredArgsConstructor
public class PointsConsumerController {

    private final PointsService pointsService;

    @Operation(summary = "我的积分余额", description = "无账户时返回 0")
    @GetMapping("/balance")
    public R<BigDecimal> balance() {
        return R.ok(pointsService.myBalance(currentUser()));
    }

    @Operation(summary = "我的积分明细", description = "分页返回当前用户的积分变动流水")
    @GetMapping("/records")
    public R<PageResult<PointsLogVO>> records(PointsLogQuery query) {
        return R.ok(pointsService.pageMyLogs(currentUser(), query));
    }

    @Operation(summary = "每日签到", description = "首次签到 +10 积分，同日重复签到幂等返回当前余额")
    @PostMapping("/sign")
    public R<BigDecimal> sign() {
        return R.ok(pointsService.sign(currentUser()));
    }

    private Long currentUser() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }
}
