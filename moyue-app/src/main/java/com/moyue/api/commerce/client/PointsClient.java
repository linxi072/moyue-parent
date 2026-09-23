package com.moyue.api.commerce.client;

import com.moyue.api.commerce.dto.PointsAccountDTO;
import com.moyue.api.commerce.dto.PointsAwardDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.points.service.PointsService;
import org.springframework.stereotype.Component;

/**
 * 积分服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-commerce) 已移除 OpenFeign，改为直接注入 {@link PointsService} 委托调用。
 */
@Component
public class PointsClient {

    private final PointsService pointsService;

    public PointsClient(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    /** 查询用户积分账户（不存在则开户） */
    public R<PointsAccountDTO> getAccount(Long userId) {
        try {
            return R.ok(pointsService.getOrCreateAccount(userId));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 服务间积分发放，返回本次入账后的新余额 */
    public R<Integer> award(PointsAwardDTO request) {
        try {
            return R.ok(pointsService.award(
                    request.getUserId(), request.getBizType(), request.getPoints(), request.getRemark()));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}
