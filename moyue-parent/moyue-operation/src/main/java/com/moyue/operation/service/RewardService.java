package com.moyue.operation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.client.BookClient;
import com.moyue.api.dto.BookSummaryDTO;
import com.moyue.api.dto.PageResult;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.operation.entity.AuthorIncomeEntity;
import com.moyue.operation.entity.RewardOrderEntity;
import com.moyue.operation.mapper.AuthorIncomeMapper;
import com.moyue.operation.mapper.RewardOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * 打赏支付链路的领域服务。
 * 路径取舍说明：打赏订单 / 稿酬流水的库表（reward_order · author_income）由本服务（operation）持有，
 * 故读者端打赏接口一并落在本服务，经网关 /api/v1/rewards/** 路由进入。
 *
 * 幂等设计：order_no 既是唯一键也是支付回调幂等键——已支付订单重复回调直接返回，不重复计入稿酬。
 */
@Service
public class RewardService {

    /** 订单状态：0 待支付 / 1 已支付 / 2 已关闭 */
    private static final int ORDER_UNPAID = 0;
    private static final int ORDER_PAID = 1;

    /** 稿酬流水类型：2 打赏分成 */
    private static final int INCOME_REWARD_SHARE = 2;

    /** 作者分成比例（70%） */
    private static final BigDecimal AUTHOR_SHARE_RATIO = new BigDecimal("0.70");

    /** 单笔打赏上限（元），防异常大额 */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000.00");

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter ORDER_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private RewardOrderMapper rewardOrderMapper;

    @Autowired
    private AuthorIncomeMapper authorIncomeMapper;

    @Autowired(required = false)
    private BookClient bookClient;

    /** 创建打赏订单（userId 由调用方从网关注入头取得，绝不信任请求体），返回待支付订单 */
    @Transactional
    public RewardOrderEntity createOrder(Long userId, Long bookId, Long chapterId,
                                         BigDecimal amount, Integer payChannel) {
        if (bookId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "作品 ID 不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "打赏金额必须大于 0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "单笔打赏金额超出上限");
        }
        RewardOrderEntity e = new RewardOrderEntity();
        e.setOrderNo(nextOrderNo());
        e.setUserId(userId);
        e.setBookId(bookId);
        e.setChapterId(chapterId);
        e.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        e.setPayChannel(payChannel == null ? 1 : payChannel);
        e.setStatus(ORDER_UNPAID);
        e.setIsDeleted(0);
        rewardOrderMapper.insert(e);
        return e;
    }

    /**
     * 支付成功回调（当前为模拟渠道回调）。
     * 幂等：已支付订单重复回调直接返回现状，不重复生成稿酬流水。
     */
    @Transactional
    public RewardOrderEntity pay(String orderNo, Long userId) {
        RewardOrderEntity order = findByOrderNo(orderNo);
        requireOwner(order, userId);
        if (order.getStatus() != null && order.getStatus() == ORDER_PAID) {
            return order;
        }
        if (order.getStatus() != null && order.getStatus() != ORDER_UNPAID) {
            throw new BizException(ResultCode.PAYMENT_FAILED, "订单当前状态不可支付");
        }
        order.setStatus(ORDER_PAID);
        order.setPayTime(LocalDateTime.now());
        rewardOrderMapper.updateById(order);
        settleAuthorIncome(order);
        return order;
    }

    /** 订单详情（仅下单人可见） */
    public RewardOrderEntity getOrder(String orderNo, Long userId) {
        RewardOrderEntity order = findByOrderNo(orderNo);
        requireOwner(order, userId);
        return order;
    }

    /** 我的打赏记录（按创建时间倒序） */
    public PageResult<RewardOrderEntity> myOrders(Long userId, int page, int size) {
        Page<RewardOrderEntity> p = new Page<>(page, size);
        IPage<RewardOrderEntity> result = rewardOrderMapper.selectPage(p,
                Wrappers.<RewardOrderEntity>lambdaQuery()
                        .eq(RewardOrderEntity::getUserId, userId)
                        .orderByDesc(RewardOrderEntity::getCreateTime));
        return toPageResult(result, page, size);
    }

    /** 我的稿酬流水（按创建时间倒序） */
    public PageResult<AuthorIncomeEntity> myIncome(Long userId, int page, int size) {
        Page<AuthorIncomeEntity> p = new Page<>(page, size);
        IPage<AuthorIncomeEntity> result = authorIncomeMapper.selectPage(p,
                Wrappers.<AuthorIncomeEntity>lambdaQuery()
                        .eq(AuthorIncomeEntity::getAuthorId, userId)
                        .orderByDesc(AuthorIncomeEntity::getCreateTime));
        return toPageResult(result, page, size);
    }

    // ------------------------------ 内部工具 ------------------------------

    /** 打赏成功 → 生成作者稿酬流水（打赏分成，按比例折算到分） */
    private void settleAuthorIncome(RewardOrderEntity order) {
        Long authorId = resolveAuthorId(order.getBookId());
        if (authorId == null) {
            // 无法确定作者（书籍服务不可用 / 作品无作者）：跳过结算，订单仍视为已支付，待对账补结
            return;
        }
        AuthorIncomeEntity income = new AuthorIncomeEntity();
        income.setAuthorId(authorId);
        income.setBookId(order.getBookId());
        income.setOrderNo(order.getOrderNo());
        income.setIncomeType(INCOME_REWARD_SHARE);
        income.setAmount(order.getAmount().multiply(AUTHOR_SHARE_RATIO).setScale(2, RoundingMode.HALF_UP));
        income.setSettleMonth(LocalDateTime.now().format(MONTH_FORMATTER));
        authorIncomeMapper.insert(income);
    }

    /** 经 Feign 取书籍作者 ID；失败或非法时返回 null（降级，不阻断支付） */
    private Long resolveAuthorId(Long bookId) {
        if (bookClient == null) {
            return null;
        }
        try {
            R<BookSummaryDTO> resp = bookClient.getBook(bookId);
            if (resp == null || resp.getCode() != ResultCode.SUCCESS.getCode() || resp.getData() == null) {
                return null;
            }
            return resp.getData().getAuthorId();
        } catch (Exception ex) {
            return null;
        }
    }

    private RewardOrderEntity findByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "订单号不能为空");
        }
        RewardOrderEntity order = rewardOrderMapper.selectOne(Wrappers.<RewardOrderEntity>lambdaQuery()
                .eq(RewardOrderEntity::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private void requireOwner(RewardOrderEntity order, Long userId) {
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    /** 订单号：RW + yyyyMMddHHmmss + 8 位随机，共 24 位（uk_order_no VARCHAR(32) 内） */
    private String nextOrderNo() {
        String ts = LocalDateTime.now().format(ORDER_TS_FORMATTER);
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "RW" + ts + rand;
    }

    private <T> PageResult<T> toPageResult(IPage<T> result, int page, int size) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }
}
