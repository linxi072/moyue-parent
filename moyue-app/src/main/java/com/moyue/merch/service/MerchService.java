package com.moyue.merch.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.merch.entity.MerchCartEntity;
import com.moyue.merch.entity.MerchOrderEntity;
import com.moyue.merch.entity.MerchProductEntity;
import com.moyue.merch.mapper.MerchCartMapper;
import com.moyue.merch.mapper.MerchOrderMapper;
import com.moyue.merch.mapper.MerchProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 商城周边业务：商品 / 购物车 / 下单 / 支付。
 * 结算与支付整体包裹在 @Transactional 内；库存扣减用数据库层条件原子更新
 * （stock >= quantity 才扣），避免并发超卖——与积分兑换同一套范式。
 */
@Service
public class MerchService {

    @Autowired
    private MerchProductMapper productMapper;

    @Autowired
    private MerchCartMapper cartMapper;

    @Autowired
    private MerchOrderMapper orderMapper;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final DateTimeFormatter ORDER_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ---------------------------------------------------------------
    // 商品
    // ---------------------------------------------------------------

    /** 上架商品列表（status=1），按创建时间倒序 */
    public PageResult<MerchProductEntity> pageOnSaleProducts(int page, int size) {
        Page<MerchProductEntity> p = new Page<>(page, size);
        QueryWrapper<MerchProductEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1).orderByDesc("create_time");
        productMapper.selectPage(p, wrapper);
        return toPage(p);
    }

    /** 商品详情（不存在或已下架抛 RESOURCE_NOT_FOUND） */
    public MerchProductEntity getProduct(Long id) {
        MerchProductEntity e = productMapper.selectById(id);
        if (e == null || e.getStatus() == null || e.getStatus() != 1) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return e;
    }

    /** 管理端：新建商品，status 默认 1 */
    public MerchProductEntity createProduct(MerchProductEntity dto) {
        MerchProductEntity e = new MerchProductEntity();
        e.setName(dto.getName());
        e.setDescription(dto.getDescription());
        e.setImageUrl(dto.getImageUrl());
        e.setPrice(dto.getPrice() == null ? BigDecimal.ZERO : dto.getPrice().setScale(2, RoundingMode.HALF_UP));
        e.setStock(dto.getStock() == null ? 0 : dto.getStock());
        e.setSales(0);
        e.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        e.setIsDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        e.setCreateTime(now);
        e.setUpdateTime(now);
        productMapper.insert(e);
        return e;
    }

    /** 管理端：更新商品，不存在抛 RESOURCE_NOT_FOUND */
    public MerchProductEntity updateProduct(Long id, MerchProductEntity dto) {
        MerchProductEntity e = productMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (dto.getName() != null) {
            e.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            e.setDescription(dto.getDescription());
        }
        if (dto.getImageUrl() != null) {
            e.setImageUrl(dto.getImageUrl());
        }
        if (dto.getPrice() != null) {
            e.setPrice(dto.getPrice().setScale(2, RoundingMode.HALF_UP));
        }
        if (dto.getStock() != null) {
            e.setStock(dto.getStock());
        }
        if (dto.getStatus() != null) {
            e.setStatus(dto.getStatus());
        }
        e.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(e);
        return e;
    }

    // ---------------------------------------------------------------
    // 购物车
    // ---------------------------------------------------------------

    /** 加入购物车：重复加入数量累加；软删行复活（uk_user_product 唯一键占用） */
    @Transactional
    public void addToCart(Long userId, Long productId, int quantity) {
        if (productId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "商品 ID 不能为空");
        }
        if (quantity <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "购买数量必须大于 0");
        }
        MerchProductEntity product = productMapper.selectById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        MerchCartEntity e = new MerchCartEntity();
        e.setUserId(userId);
        e.setProductId(productId);
        e.setQuantity(quantity);
        e.setIsDeleted(0);
        e.setCreateTime(LocalDateTime.now());
        try {
            cartMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            // 已在购物车（或曾移出留软删行）：复活并累加数量
            cartMapper.revive(userId, productId, quantity);
        }
    }

    /** 移出购物车（全局逻辑删除） */
    @Transactional
    public void removeCartItem(Long userId, Long productId) {
        MerchCartEntity e = findActiveCart(userId, productId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        cartMapper.deleteById(e.getId());
    }

    /** 购物车列表（带商品信息，按加入时间倒序） */
    public List<CartItemView> listCart(Long userId) {
        QueryWrapper<MerchCartEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time");
        List<MerchCartEntity> carts = cartMapper.selectList(wrapper);
        List<CartItemView> views = new ArrayList<>();
        for (MerchCartEntity cart : carts) {
            MerchProductEntity product = productMapper.selectById(cart.getProductId());
            // 商品已下架/删除时仍展示快照信息，允许用户自行移除
            views.add(new CartItemView(cart.getId(), cart.getProductId(),
                    product == null ? "（商品已不存在）" : product.getName(),
                    product == null ? null : product.getImageUrl(),
                    product == null ? null : product.getPrice(),
                    cart.getQuantity()));
        }
        return views;
    }

    // ---------------------------------------------------------------
    // 下单 / 支付
    // ---------------------------------------------------------------

    /**
     * 结算购物车（P0 核心链路）：一次结算一个 order_no，按购物车行拆单。
     * 每行库存扣减为条件原子更新（status=1 AND stock>=quantity），任一行失败整单回滚；
     * 成功后清空（逻辑删除）购物车。购物车为空抛 PARAM_ERROR。
     */
    @Transactional
    public List<MerchOrderEntity> checkout(Long userId) {
        QueryWrapper<MerchCartEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time");
        List<MerchCartEntity> carts = cartMapper.selectList(wrapper);
        if (carts.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "购物车为空");
        }

        String orderNo = generateOrderNo();
        LocalDateTime now = LocalDateTime.now();
        List<MerchOrderEntity> orders = new ArrayList<>();
        for (MerchCartEntity cart : carts) {
            MerchProductEntity product = productMapper.selectById(cart.getProductId());
            if (product == null || product.getStatus() == null || product.getStatus() != 1) {
                throw new BizException(ResultCode.PARAM_ERROR, "购物车内存在已下架商品，请移除后重试");
            }
            int qty = cart.getQuantity() == null ? 1 : cart.getQuantity();
            // 条件原子扣库存并累加销量：影响行数为 0 说明库存不足或已下架
            LambdaUpdateWrapper<MerchProductEntity> stockWrapper = new LambdaUpdateWrapper<>();
            stockWrapper.eq(MerchProductEntity::getId, product.getId())
                    .eq(MerchProductEntity::getStatus, 1)
                    .ge(MerchProductEntity::getStock, qty)
                    .setSql("stock = stock - " + qty
                            + ", sales = sales + " + qty
                            + ", update_time = NOW()");
            int rows = productMapper.update(null, stockWrapper);
            if (rows == 0) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "商品「" + product.getName() + "」库存不足");
            }

            MerchOrderEntity order = new MerchOrderEntity();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setProductId(product.getId());
            order.setProductName(product.getName());
            order.setQuantity(qty);
            order.setTotalAmount(product.getPrice()
                    .multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
            order.setStatus(0);
            order.setIsDeleted(0);
            order.setCreateTime(now);
            order.setUpdateTime(now);
            orderMapper.insert(order);
            orders.add(order);

            cartMapper.deleteById(cart.getId());
        }
        return orders;
    }

    /**
     * 按 orderNo 整单支付（幂等）：status 0→1 条件原子更新；
     * 订单不存在（或不属于该用户）抛 RESOURCE_NOT_FOUND，重复支付直接返回成功。
     */
    @Transactional
    public List<MerchOrderEntity> pay(String orderNo, Long userId) {
        QueryWrapper<MerchOrderEntity> qw = new QueryWrapper<>();
        qw.eq("order_no", orderNo).eq("user_id", userId);
        List<MerchOrderEntity> orders = orderMapper.selectList(qw);
        if (orders.isEmpty()) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        LambdaUpdateWrapper<MerchOrderEntity> payWrapper = new LambdaUpdateWrapper<>();
        payWrapper.eq(MerchOrderEntity::getOrderNo, orderNo)
                .eq(MerchOrderEntity::getUserId, userId)
                .eq(MerchOrderEntity::getStatus, 0)
                .setSql("status = 1, update_time = NOW()");
        orderMapper.update(null, payWrapper);

        // 重新查出最新状态返回（幂等：已支付订单不变）
        return orderMapper.selectList(qw);
    }

    /** 某用户订单列表（一行一商品，按下单时间倒序） */
    public PageResult<MerchOrderEntity> pageOrders(Long userId, int page, int size) {
        Page<MerchOrderEntity> p = new Page<>(page, size);
        QueryWrapper<MerchOrderEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time");
        orderMapper.selectPage(p, wrapper);
        return toPage(p);
    }

    // ---------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------

    /** 查当前用户在该商品上的有效购物车行 */
    private MerchCartEntity findActiveCart(Long userId, Long productId) {
        QueryWrapper<MerchCartEntity> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("product_id", productId);
        return cartMapper.selectOne(qw);
    }

    /** 订单号：14 位时间戳 + 10 位随机数，共 24 位（与打赏订单同风格） */
    private String generateOrderNo() {
        StringBuilder sb = new StringBuilder(LocalDateTime.now().format(ORDER_TS));
        for (int i = 0; i < 10; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** MP Page → 统一分页结果（泛型，商品/订单共用） */
    private <T> PageResult<T> toPage(Page<T> p) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        result.setRecords(p.getRecords());
        return result;
    }

    // ---------------------------------------------------------------
    // 出参视图
    // ---------------------------------------------------------------

    /** 购物车行视图：购物车 + 商品快照 */
    public static class CartItemView {

        private Long cartId;
        private Long productId;
        private String productName;
        private String imageUrl;
        private BigDecimal price;
        private Integer quantity;

        public CartItemView(Long cartId, Long productId, String productName,
                            String imageUrl, BigDecimal price, Integer quantity) {
            this.cartId = cartId;
            this.productId = productId;
            this.productName = productName;
            this.imageUrl = imageUrl;
            this.price = price;
            this.quantity = quantity;
        }

        public Long getCartId() {
            return cartId;
        }

        public Long getProductId() {
            return productId;
        }

        public String getProductName() {
            return productName;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public Integer getQuantity() {
            return quantity;
        }
    }
}
