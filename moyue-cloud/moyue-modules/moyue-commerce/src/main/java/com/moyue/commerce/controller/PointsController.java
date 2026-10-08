package com.moyue.commerce.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.commerce.domain.dto.query.PointsAccountQuery;
import com.moyue.commerce.domain.dto.query.PointsLogQuery;
import com.moyue.commerce.domain.vo.PointsAccountVO;
import com.moyue.commerce.domain.vo.PointsLogVO;
import com.moyue.commerce.service.PointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 积分账户管理（商业化域）：账户 / 流水分页 + 运营手动调整积分。
 *
 * @author moyue
 */
@Tag(name = "积分账户", description = "用户积分账户与流水查询、运营手动调整")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/commerce/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    @Operation(summary = "账户分页")
    @RequiresPermissions("commerce:points:list")
    @GetMapping
    public R<PageResult<PointsAccountVO>> page(PointsAccountQuery query) {
        return R.ok(pointsService.pageAccounts(query));
    }

    @Operation(summary = "流水分页")
    @RequiresPermissions("commerce:points:list")
    @GetMapping("/logs")
    public R<PageResult<PointsLogVO>> logs(PointsLogQuery query) {
        return R.ok(pointsService.pageLogs(query));
    }

    @Operation(summary = "手动调整积分", description = "正数充值 / 打赏，负数消费 / 退款；余额不足会失败")
    @RequiresPermissions("commerce:points:adjust")
    @Log(title = "积分账户", businessType = BusinessType.UPDATE)
    @PostMapping("/adjust")
    public R<BigDecimal> adjust(@RequestParam Long userId,
                                @RequestParam int bizType,
                                @RequestParam BigDecimal amount,
                                @RequestParam(required = false) String remark) {
        return R.ok(pointsService.changePoints(userId, bizType, amount, "ADMIN:" + System.nanoTime(), remark));
    }
}
