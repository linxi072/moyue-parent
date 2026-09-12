package com.moyue.points.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.dto.PageResult;
import com.moyue.api.dto.PointsAccountDTO;
import com.moyue.api.dto.PointsOrderDTO;
import com.moyue.api.dto.PointsProductDTO;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.points.entity.PointsAccountEntity;
import com.moyue.points.entity.PointsOrderEntity;
import com.moyue.points.entity.PointsProductEntity;
import com.moyue.points.mapper.PointsAccountMapper;
import com.moyue.points.mapper.PointsOrderMapper;
import com.moyue.points.mapper.PointsProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 积分商城业务：账户查询/初始化、商品上下架、兑换下单。
 * 兑换逻辑整体包裹在 @Transactional 内，保证余额扣减 / 库存扣减 / 订单插入原子。
 */
@Service
public class PointsService {

    @Autowired
    private PointsAccountMapper accountMapper;

    @Autowired
    private PointsProductMapper productMapper;

    @Autowired
    private PointsOrderMapper orderMapper;

    /** 查询积分账户；若不存在则插入一行(余额0/累计获得0/累计消费0)再返回 */
    public PointsAccountDTO getOrCreateAccount(Long userId) {
        PointsAccountEntity account = accountMapper.selectById(userId);
        if (account == null) {
            account = new PointsAccountEntity();
            account.setUserId(userId);
            account.setBalance(0);
            account.setTotalEarned(0);
            account.setTotalSpent(0);
            LocalDateTime now = LocalDateTime.now();
            account.setCreateTime(now);
            account.setUpdateTime(now);
            accountMapper.insert(account);
        }
        return toAccountDTO(account);
    }

    /** 上架商品列表（status=1），按 create_time 倒序 */
    public PageResult<PointsProductDTO> pageOnSaleProducts(int page, int size) {
        Page<PointsProductEntity> p = new Page<>(page, size);
        QueryWrapper<PointsProductEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1).orderByDesc("create_time");
        productMapper.selectPage(p, wrapper);
        return toProductPage(p);
    }

    /** 管理端：列出全部商品（含下架），按 create_time 倒序 */
    public PageResult<PointsProductDTO> pageAllProducts(int page, int size) {
        Page<PointsProductEntity> p = new Page<>(page, size);
        QueryWrapper<PointsProductEntity> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        productMapper.selectPage(p, wrapper);
        return toProductPage(p);
    }

    /** 管理端：新建商品，status 默认 1 */
    public PointsProductDTO createProduct(PointsProductDTO dto) {
        PointsProductEntity product = new PointsProductEntity();
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setImageUrl(dto.getImageUrl());
        product.setCostPoints(dto.getCostPoints());
        product.setStock(dto.getStock());
        product.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        LocalDateTime now = LocalDateTime.now();
        product.setCreateTime(now);
        product.setUpdateTime(now);
        productMapper.insert(product);
        return toProductDTO(product);
    }

    /** 管理端：更新商品（可改 name/description/imageUrl/costPoints/stock/status），不存在抛 RESOURCE_NOT_FOUND */
    public PointsProductDTO updateProduct(Long id, PointsProductDTO dto) {
        PointsProductEntity product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (dto.getName() != null) {
            product.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            product.setDescription(dto.getDescription());
        }
        if (dto.getImageUrl() != null) {
            product.setImageUrl(dto.getImageUrl());
        }
        if (dto.getCostPoints() != null) {
            product.setCostPoints(dto.getCostPoints());
        }
        if (dto.getStock() != null) {
            product.setStock(dto.getStock());
        }
        if (dto.getStatus() != null) {
            product.setStatus(dto.getStatus());
        }
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        return toProductDTO(product);
    }

    /** 某用户兑换订单列表，按 create_time 倒序 */
    public PageResult<PointsOrderDTO> pageOrders(Long userId, int page, int size) {
        Page<PointsOrderEntity> p = new Page<>(page, size);
        QueryWrapper<PointsOrderEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time");
        orderMapper.selectPage(p, wrapper);
        return toOrderPage(p);
    }

    /**
     * 创建兑换订单。整体在事务内，保证余额扣减 / 库存扣减 / 订单插入原子。
     * 并发安全：库存与余额均改为数据库层条件原子更新（stock = stock - 1 / balance = balance - cost），
     * 以 UPDATE 影响行数判定成败，避免「读快照 → 内存算 → 绝对值回写」造成的超卖与余额扣减丢失。
     * 校验：商品存在且 status=1 且 stock>0 为快速失败；真正的并发安全由条件更新兜底；账户不足则先建。
     */
    @Transactional
    public PointsOrderDTO createOrder(Long userId, Long productId) {
        PointsProductEntity product = productMapper.selectById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (product.getStock() == null || product.getStock() <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "商品库存不足");
        }

        PointsAccountEntity account = accountMapper.selectById(userId);
        if (account == null) {
            account = new PointsAccountEntity();
            account.setUserId(userId);
            account.setBalance(0);
            account.setTotalEarned(0);
            account.setTotalSpent(0);
            LocalDateTime now = LocalDateTime.now();
            account.setCreateTime(now);
            account.setUpdateTime(now);
            // 同一用户首次并发兑换时，两个事务可能同时走到这里，主键 user_id 冲突。
            // MySQL 下唯一键冲突只回滚当前语句、不会中止整个事务，因此可以安全吞掉并继续——
            // 请勿在此抛异常或做补偿：冲突恰恰说明该账户行已由另一个事务建好，
            // 紧随其后的余额扣减是条件更新（WHERE user_id = ? AND balance >= ? 且 balance = balance - cost），
            // 对这行同样生效。
            try {
                accountMapper.insert(account);
            } catch (DuplicateKeyException ex) {
                // 账户行已存在，直接走下方条件扣减即可
            }
        }

        // 兑换所需积分：该值将拼接进下方 setSql，必须先确认为合法非负数值再使用
        Integer costPoints = product.getCostPoints();
        if (costPoints == null || costPoints < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "商品兑换积分配置非法");
        }
        int cost = costPoints;

        // 扣减库存：条件原子更新，仅当 id 命中且 status=1 且 stock>0 时才执行 stock = stock - 1；
        // 影响行数为 0 说明库存不足或商品已下架，避免读改写导致的并发超卖
        LambdaUpdateWrapper<PointsProductEntity> stockWrapper = new LambdaUpdateWrapper<>();
        stockWrapper.eq(PointsProductEntity::getId, productId)
                .eq(PointsProductEntity::getStatus, 1)
                .gt(PointsProductEntity::getStock, 0)
                .setSql("stock = stock - 1, update_time = NOW()");
        int stockRows = productMapper.update(null, stockWrapper);
        if (stockRows == 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "商品库存不足");
        }

        // 扣减账户余额并累加消费：条件原子更新，仅当 user_id 命中且 balance >= cost 时才扣减；
        // 影响行数为 0 说明积分不足，避免读改写导致的余额扣减丢失
        LambdaUpdateWrapper<PointsAccountEntity> balanceWrapper = new LambdaUpdateWrapper<>();
        balanceWrapper.eq(PointsAccountEntity::getUserId, userId)
                .ge(PointsAccountEntity::getBalance, cost)
                .setSql("balance = balance - " + cost
                        + ", total_spent = total_spent + " + cost
                        + ", update_time = NOW()");
        int balanceRows = accountMapper.update(null, balanceWrapper);
        if (balanceRows == 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "积分不足");
        }

        // 插入兑换订单（status=1 已兑换，商品名快照）
        PointsOrderEntity order = new PointsOrderEntity();
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductName(product.getName());
        order.setCostPoints(cost);
        order.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        order.setCreateTime(now);
        order.setUpdateTime(now);
        orderMapper.insert(order);

        return toOrderDTO(order);
    }

    // ---------------------------------------------------------------
    // 实体 -> DTO 组装
    // ---------------------------------------------------------------

    private PointsAccountDTO toAccountDTO(PointsAccountEntity e) {
        PointsAccountDTO dto = new PointsAccountDTO();
        dto.setUserId(e.getUserId());
        dto.setBalance(e.getBalance());
        dto.setTotalEarned(e.getTotalEarned());
        dto.setTotalSpent(e.getTotalSpent());
        dto.setCreateTime(e.getCreateTime());
        dto.setUpdateTime(e.getUpdateTime());
        return dto;
    }

    private PointsProductDTO toProductDTO(PointsProductEntity e) {
        PointsProductDTO dto = new PointsProductDTO();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setDescription(e.getDescription());
        dto.setImageUrl(e.getImageUrl());
        dto.setCostPoints(e.getCostPoints());
        dto.setStock(e.getStock());
        dto.setStatus(e.getStatus());
        dto.setCreateTime(e.getCreateTime());
        dto.setUpdateTime(e.getUpdateTime());
        return dto;
    }

    private PointsOrderDTO toOrderDTO(PointsOrderEntity e) {
        PointsOrderDTO dto = new PointsOrderDTO();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setProductId(e.getProductId());
        dto.setProductName(e.getProductName());
        dto.setCostPoints(e.getCostPoints());
        dto.setStatus(e.getStatus());
        dto.setCreateTime(e.getCreateTime());
        dto.setUpdateTime(e.getUpdateTime());
        return dto;
    }

    private PageResult<PointsProductDTO> toProductPage(Page<PointsProductEntity> p) {
        PageResult<PointsProductDTO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        List<PointsProductDTO> records = p.getRecords().stream()
                .map(this::toProductDTO)
                .collect(Collectors.toList());
        result.setRecords(records);
        return result;
    }

    private PageResult<PointsOrderDTO> toOrderPage(Page<PointsOrderEntity> p) {
        PageResult<PointsOrderDTO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        List<PointsOrderDTO> records = p.getRecords().stream()
                .map(this::toOrderDTO)
                .collect(Collectors.toList());
        result.setRecords(records);
        return result;
    }
}
