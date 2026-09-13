package com.moyue.operation.controller;

import com.moyue.common.core.domain.PageResult;
import com.moyue.common.R;
import com.moyue.operation.entity.RewardOrderEntity;
import com.moyue.operation.service.OperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营接口：打赏订单对账。
 * 完整路径 /api/v1/admin/orders，与网关 /api/v1/admin/orders/** 路由匹配。
 * 说明：公告已迁至 {@link AnnouncementController}（/api/v1/admin/announcements），
 * 此前公告路径被复用为订单列表的占位实现，现已各归其位。
 */
@RestController
@RequestMapping("/api/v1")
public class OperationController {

    @Autowired
    private OperationService operationService;

    /** 打赏订单分页（运营对账）：GET /api/v1/admin/orders */
    @GetMapping("/admin/orders")
    public R<PageResult<RewardOrderEntity>> orders(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return R.ok(operationService.listOrders(page, size));
    }
}
