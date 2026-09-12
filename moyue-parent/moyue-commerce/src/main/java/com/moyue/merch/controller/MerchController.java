package com.moyue.merch.controller;

import com.moyue.api.dto.PageResult;
import com.moyue.common.R;
import com.moyue.merch.entity.MerchOrderEntity;
import com.moyue.merch.entity.MerchProductEntity;
import com.moyue.merch.service.MerchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商城周边接口：商品 / 购物车 / 下单支付。
 * 路径前缀 /api/v1 与网关路由保持一致；管理端 /api/v1/admin/merch/** 由
 * common 的 AdminRoleInterceptor 做 role=3 断言。
 * userId 口径：本服务演示期由前端/调用方显式传入（与 points 模块一致）。
 */
@RestController
@RequestMapping("/api/v1")
public class MerchController {

    @Autowired
    private MerchService merchService;

    /** 上架商品列表（status=1），按创建时间倒序 */
    @GetMapping("/merch/products")
    public R<PageResult<MerchProductEntity>> listProducts(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return R.ok(merchService.pageOnSaleProducts(page, size));
    }

    /** 商品详情（已下架/不存在返回 20001） */
    @GetMapping("/merch/products/{id}")
    public R<MerchProductEntity> getProduct(@PathVariable Long id) {
        return R.ok(merchService.getProduct(id));
    }

    /** 购物车列表（带商品快照，按加入时间倒序） */
    @GetMapping("/merch/cart")
    public R<List<MerchService.CartItemView>> listCart(@RequestParam Long userId) {
        return R.ok(merchService.listCart(userId));
    }

    /** 加入购物车：数量必须 > 0；重复加入数量累加 */
    @PostMapping("/merch/cart")
    public R<Void> addToCart(@RequestBody CartRequest req) {
        merchService.addToCart(req.getUserId(), req.getProductId(),
                req.getQuantity() == null ? 1 : req.getQuantity());
        return R.ok();
    }

    /** 移出购物车 */
    @DeleteMapping("/merch/cart/{productId}")
    public R<Void> removeCartItem(@PathVariable Long productId, @RequestParam Long userId) {
        merchService.removeCartItem(userId, productId);
        return R.ok();
    }

    /** 结算购物车：生成一个 orderNo 按行拆单，成功后清空购物车 */
    @PostMapping("/merch/orders")
    public R<List<MerchOrderEntity>> checkout(@RequestBody CheckoutRequest req) {
        return R.ok(merchService.checkout(req.getUserId()));
    }

    /** 按 orderNo 整单支付（幂等） */
    @PostMapping("/merch/orders/{orderNo}/pay")
    public R<List<MerchOrderEntity>> pay(@PathVariable String orderNo, @RequestBody PayRequest req) {
        return R.ok(merchService.pay(orderNo, req.getUserId()));
    }

    /** 某用户订单列表（一行一商品，按下单时间倒序） */
    @GetMapping("/merch/orders")
    public R<PageResult<MerchOrderEntity>> listOrders(@RequestParam Long userId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return R.ok(merchService.pageOrders(userId, page, size));
    }

    // ------------------------------ 管理端 ------------------------------

    /** 管理端：新建周边商品 */
    @PostMapping("/admin/merch/products")
    public R<MerchProductEntity> createProduct(@RequestBody MerchProductEntity product) {
        return R.ok(merchService.createProduct(product));
    }

    /** 管理端：更新周边商品 */
    @PutMapping("/admin/merch/products/{id}")
    public R<MerchProductEntity> updateProduct(@PathVariable Long id,
                                               @RequestBody MerchProductEntity product) {
        return R.ok(merchService.updateProduct(id, product));
    }

    // ------------------------------ 请求体 ------------------------------

    /** 加入购物车请求 */
    public static class CartRequest {

        private Long userId;

        private Long productId;

        private Integer quantity;

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

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }

    /** 结算请求 */
    public static class CheckoutRequest {

        private Long userId;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }

    /** 支付请求 */
    public static class PayRequest {

        private Long userId;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }
}
