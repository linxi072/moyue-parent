package com.moyue.commerce.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.commerce.domain.dto.query.ProductQuery;
import com.moyue.commerce.domain.entity.Product;
import com.moyue.commerce.domain.vo.ProductVO;
import com.moyue.commerce.service.ProductService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分兑换商品管理（商业化域）：CRUD + 上架 / 下架 + 兑换闭环。
 *
 * @author moyue
 */
@Tag(name = "兑换商品", description = "书币 / 会员等兑换商品增删改查、上架下架与兑换")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/commerce/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "商品分页")
    @RequiresPermissions("commerce:product:list")
    @GetMapping
    public R<PageResult<ProductVO>> page(ProductQuery query) {
        return R.ok(productService.pageProducts(query));
    }

    @Operation(summary = "新建商品")
    @RequiresPermissions("commerce:product:add")
    @Log(title = "兑换商品", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody Product entity) {
        return R.ok(productService.createProduct(entity));
    }

    @Operation(summary = "编辑商品")
    @RequiresPermissions("commerce:product:edit")
    @Log(title = "兑换商品", businessType = BusinessType.UPDATE)
    @PutMapping("/{productId}")
    public R<Boolean> update(@PathVariable Long productId, @RequestBody Product entity) {
        entity.setId(productId);
        return R.ok(productService.updateProduct(entity));
    }

    @Operation(summary = "删除商品")
    @RequiresPermissions("commerce:product:remove")
    @Log(title = "兑换商品", businessType = BusinessType.DELETE)
    @DeleteMapping("/{productId}")
    public R<Boolean> delete(@PathVariable Long productId) {
        return R.ok(productService.deleteProduct(productId));
    }

    @Operation(summary = "上架")
    @RequiresPermissions("commerce:product:edit")
    @Log(title = "兑换商品", businessType = BusinessType.UPDATE)
    @PostMapping("/{productId}/online")
    public R<Boolean> online(@PathVariable Long productId) {
        return R.ok(productService.online(productId));
    }

    @Operation(summary = "下架")
    @RequiresPermissions("commerce:product:edit")
    @Log(title = "兑换商品", businessType = BusinessType.UPDATE)
    @PostMapping("/{productId}/offline")
    public R<Boolean> offline(@PathVariable Long productId) {
        return R.ok(productService.offline(productId));
    }

    @Operation(summary = "积分兑换", description = "校验上架 + 扣积分 + 减库存 + 落订单；返回支付订单 ID")
    @RequiresPermissions("commerce:product:exchange")
    @Log(title = "兑换商品", businessType = BusinessType.UPDATE)
    @PostMapping("/{productId}/exchange")
    public R<Long> exchange(@PathVariable Long productId, @RequestParam Long userId) {
        return R.ok(productService.exchange(productId, userId));
    }
}
