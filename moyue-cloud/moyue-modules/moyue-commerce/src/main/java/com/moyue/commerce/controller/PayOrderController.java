package com.moyue.commerce.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.commerce.domain.dto.query.PayOrderQuery;
import com.moyue.commerce.domain.entity.PayOrder;
import com.moyue.commerce.domain.vo.PayOrderVO;
import com.moyue.commerce.service.PayOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 付费订单管理（商业化域）：CRUD + 订单概览。
 *
 * @author moyue
 */
@Tag(name = "付费订单", description = "书币 / 会员 / 打赏订单增删改查与概览")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/commerce/orders")
@RequiredArgsConstructor
public class PayOrderController {

    private final PayOrderService orderService;

    @Operation(summary = "订单分页")
    @RequiresPermissions("commerce:order:list")
    @GetMapping
    public R<PageResult<PayOrderVO>> page(PayOrderQuery query) {
        return R.ok(orderService.pageOrders(query));
    }

    @Operation(summary = "新建订单")
    @RequiresPermissions("commerce:order:add")
    @Log(title = "付费订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody PayOrder entity) {
        return R.ok(orderService.createOrder(entity));
    }

    @Operation(summary = "编辑订单")
    @RequiresPermissions("commerce:order:edit")
    @Log(title = "付费订单", businessType = BusinessType.UPDATE)
    @PutMapping("/{orderId}")
    public R<Boolean> update(@PathVariable Long orderId, @RequestBody PayOrder entity) {
        entity.setId(orderId);
        return R.ok(orderService.updateOrder(entity));
    }

    @Operation(summary = "删除订单")
    @RequiresPermissions("commerce:order:remove")
    @Log(title = "付费订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderId}")
    public R<Boolean> delete(@PathVariable Long orderId) {
        return R.ok(orderService.deleteOrder(orderId));
    }

    @Operation(summary = "退款", description = "仅已付订单可退，幂等；真实退款到第三方为结构占位")
    @RequiresPermissions("commerce:order:refund")
    @Log(title = "付费订单", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderId}/refund")
    public R<Boolean> refund(@PathVariable Long orderId) {
        return R.ok(orderService.refundOrder(orderId));
    }

    @Operation(summary = "订单概览", description = "总数 / 已付 / 退款 / 已付总额")
    @RequiresPermissions("commerce:order:list")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(orderService.summary());
    }
}
