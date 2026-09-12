package com.moyue.points.controller;

import com.moyue.api.dto.PageResult;
import com.moyue.api.dto.PointsAccountDTO;
import com.moyue.api.dto.PointsOrderDTO;
import com.moyue.api.dto.PointsProductDTO;
import com.moyue.common.R;
import com.moyue.points.service.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分商城接口。
 * 路径前缀 /api/v1 与网关路由、Feign PointsClient 保持一致。
 * 管理端端点由网关 /api/v1/admin/points/** 路由至此，此处不做权限拦截。
 */
@RestController
@RequestMapping("/api/v1")
public class PointsController {

    @Autowired
    private PointsService pointsService;

    /** 查询积分账户（不存在则初始化一行再返回），供 Feign PointsClient.getAccount 调用 */
    @GetMapping("/points/accounts/{userId}")
    public R<PointsAccountDTO> getAccount(@PathVariable Long userId) {
        return R.ok(pointsService.getOrCreateAccount(userId));
    }

    /** 上架商品列表（status=1），按创建时间倒序 */
    @GetMapping("/points/products")
    public R<PageResult<PointsProductDTO>> listProducts(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return R.ok(pointsService.pageOnSaleProducts(page, size));
    }

    /** 创建兑换订单：入参 {userId, productId} */
    @PostMapping("/points/orders")
    public R<PointsOrderDTO> createOrder(@RequestBody CreateOrderRequest request) {
        return R.ok(pointsService.createOrder(request.getUserId(), request.getProductId()));
    }

    /** 某用户兑换订单列表，按创建时间倒序 */
    @GetMapping("/points/orders")
    public R<PageResult<PointsOrderDTO>> listOrders(@RequestParam Long userId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return R.ok(pointsService.pageOrders(userId, page, size));
    }

    /** 管理端：新建商品 */
    @PostMapping("/admin/points/products")
    public R<PointsProductDTO> createProduct(@RequestBody PointsProductDTO product) {
        return R.ok(pointsService.createProduct(product));
    }

    /** 管理端：更新商品（id 取路径，其余字段可改） */
    @PutMapping("/admin/points/products/{id}")
    public R<PointsProductDTO> updateProduct(@PathVariable Long id,
                                             @RequestBody PointsProductDTO product) {
        return R.ok(pointsService.updateProduct(id, product));
    }

    /** 管理端：列出全部商品（含下架），按创建时间倒序 */
    @GetMapping("/admin/points/products")
    public R<PageResult<PointsProductDTO>> listAllProducts(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return R.ok(pointsService.pageAllProducts(page, size));
    }

    /** 创建兑换订单请求体 */
    public static class CreateOrderRequest {

        private Long userId;

        private Long productId;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }
    }
}
