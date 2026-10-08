package com.moyue.commerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.commerce.domain.dto.query.ProductQuery;
import com.moyue.commerce.domain.entity.PayOrder;
import com.moyue.commerce.domain.entity.PointsLog;
import com.moyue.commerce.domain.entity.Product;
import com.moyue.commerce.domain.vo.ProductVO;
import com.moyue.commerce.mapper.PayOrderMapper;
import com.moyue.commerce.mapper.ProductMapper;
import com.moyue.commerce.service.ProductService;
import com.moyue.commerce.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 积分兑换商品服务实现：上架 / 下架 / 库存 + 兑换闭环。
 *
 * <p>兑换（exchange）为保证一致性放在同一事务内：扣积分 → 减库存 → 落支付订单。
 * 真实支付网关为结构占位（沙箱无凭证），此处直接标记订单为已付（status=1）。</p>
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    /** 积分兑换走「余额 / 积分」渠道 */
    private static final int PAY_CHANNEL_POINTS = 3;

    private final ProductMapper productMapper;
    private final PointsService pointsService;
    private final PayOrderMapper orderMapper;

    @Override
    public PageResult<ProductVO> pageProducts(ProductQuery query) {
        var page = PageUtils.<Product>page(query);
        var result = productMapper.selectPage(page, new LambdaQueryWrapper<Product>()
                .like(StringUtils.hasText(query.getName()), Product::getName, query.getName())
                .eq(query.getType() != null, Product::getType, query.getType())
                .eq(query.getStatus() != null, Product::getStatus, query.getStatus())
                .orderByDesc(Product::getStatus)
                .orderByDesc(Product::getSort)
                .orderByDesc(Product::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProduct(Product entity) {
        if (!StringUtils.hasText(entity.getName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "商品名称不能为空");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(0);
        }
        if (entity.getStock() == null) {
            entity.setStock(0);
        }
        if (entity.getPoints() == null) {
            entity.setPoints(0);
        }
        if (entity.getSort() == null) {
            entity.setSort(0);
        }
        productMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProduct(Product entity) {
        Product exist = productMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("兑换商品");
        }
        return productMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProduct(Long productId) {
        Product exist = productMapper.selectById(productId);
        if (exist == null) {
            throw BusinessException.notFound("兑换商品");
        }
        return productMapper.deleteById(productId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean online(Long productId) {
        Product exist = productMapper.selectById(productId);
        if (exist == null) {
            throw BusinessException.notFound("兑换商品");
        }
        Product upd = new Product();
        upd.setId(productId);
        upd.setStatus(1);
        return productMapper.updateById(upd) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean offline(Long productId) {
        Product exist = productMapper.selectById(productId);
        if (exist == null) {
            throw BusinessException.notFound("兑换商品");
        }
        Product upd = new Product();
        upd.setId(productId);
        upd.setStatus(0);
        return productMapper.updateById(upd) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long exchange(Long productId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "兑换用户不能为空");
        }
        Product p = productMapper.selectById(productId);
        if (p == null) {
            throw BusinessException.notFound("兑换商品");
        }
        if (p.getStatus() == null || p.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "商品未上架，无法兑换");
        }
        if (p.getPoints() == null || p.getPoints() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "商品兑换积分未配置");
        }

        // 1) 扣积分（不足会抛 PAY_FAILED，事务回滚）
        pointsService.changePoints(userId, PointsLog.BIZ_CONSUME, BigDecimal.valueOf(p.getPoints()).negate(),
                "EXCHANGE:" + productId, "积分兑换：" + p.getName());

        // 2) 原子减库存（stock>0 才成功）
        int affected = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getStatus, 1)
                .gt(Product::getStock, 0)
                .setSql("stock = stock - 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "商品库存不足，兑换失败");
        }

        // 3) 落支付订单（真实支付网关为结构占位，直接置已付）
        PayOrder order = new PayOrder();
        order.setUserId(userId);
        order.setProductName(p.getName());
        order.setProductType(p.getType());
        order.setAmount(p.getPriceAmount() == null ? BigDecimal.ZERO : p.getPriceAmount());
        order.setQuantity(1);
        order.setPayChannel(PAY_CHANNEL_POINTS);
        order.setStatus(1);
        order.setOrderNo("EX" + System.nanoTime());
        orderMapper.insert(order);
        log.info("用户 {} 兑换商品 {}（积分 -{}），生成订单 {}", userId, productId, p.getPoints(), order.getId());
        return order.getId();
    }

    private ProductVO toVO(Product e) {
        return ProductVO.builder()
                .id(e.getId())
                .name(e.getName())
                .type(e.getType())
                .priceAmount(e.getPriceAmount())
                .points(e.getPoints())
                .stock(e.getStock())
                .status(e.getStatus())
                .sort(e.getSort())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}
