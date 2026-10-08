package com.moyue.commerce.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.commerce.domain.dto.query.ProductQuery;
import com.moyue.commerce.domain.entity.Product;
import com.moyue.commerce.domain.vo.ProductVO;

/**
 * 积分兑换商品服务：上架 / 下架 / 库存 + 兑换（扣积分 + 落订单）。
 *
 * @author moyue
 */
public interface ProductService {

    /** 商品分页 */
    PageResult<ProductVO> pageProducts(ProductQuery query);

    /** 新建商品 */
    Long createProduct(Product entity);

    /** 编辑商品 */
    boolean updateProduct(Product entity);

    /** 删除商品（逻辑删除） */
    boolean deleteProduct(Long productId);

    /** 上架 */
    boolean online(Long productId);

    /** 下架 */
    boolean offline(Long productId);

    /**
     * 积分兑换：校验上架 + 扣积分（事务）+ 减库存 + 落支付订单。
     *
     * @param productId 商品 ID
     * @param userId    兑换用户
     * @return 生成的支付订单 ID
     */
    Long exchange(Long productId, Long userId);
}
