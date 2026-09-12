package com.moyue.operation.controller;

import com.moyue.api.dto.PageResult;
import com.moyue.common.R;
import com.moyue.operation.entity.RewardOrderEntity;
import com.moyue.operation.service.OperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营接口：打赏订单对账 / 公告（占位）。
 * 网关将 /api/v1/admin/announcements 路由到本服务，
 * 此处以打赏订单列表作为运营对账数据的占位返回，后续可替换为真实公告 / 对账逻辑。
 */
@RestController
@RequestMapping("/api/v1")
public class OperationController {

    @Autowired
    private OperationService operationService;

    @GetMapping("/admin/announcements")
    public R<PageResult<RewardOrderEntity>> announcements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(operationService.listOrders(page, size));
    }
}
