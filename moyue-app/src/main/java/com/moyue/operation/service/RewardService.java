package com.moyue.operation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.content.client.BookClient;
import com.moyue.api.content.client.ChapterClient;
import com.moyue.api.risk.client.BehaviorRiskClient;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.operation.entity.AuthorIncomeEntity;
import com.moyue.operation.entity.RewardOrderEntity;
import com.moyue.operation.event.RewardDynamicEvent;
import com.moyue.operation.mapper.AuthorIncomeMapper;
import com.moyue.operation.mapper.RewardOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
@Slf4j
public class RewardService {

    /** 订单状态：0 待支付 / 1 已支付 / 2 已关闭 */
    private static final int ORDER_UNPAID = 0;
    private static final int ORDER_PAID = 1;

    /** 稿酬流水类型：2 打赏分成 */
    private static final int INCOME_REWARD_SHARE = 2;

    /** 稿酬流水类型：4 买断分成（无真实付费域，由管理端手工录入，100% 入账） */
    private static final int INCOME_BUYOUT_SHARE = 4;

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

    /** 章节客户端（16-23 标题 enrichment）；不可用时标题留空，不阻断主流程 */
    @Autowired(required = false)
    private ChapterClient chapterClient;

    /** 行为风控客户端（P2-C 闭环补全）：打赏支付成功后非阻断埋点 */
    @Autowired(required = false)
    private BehaviorRiskClient behaviorRiskClient;

    /** 打赏动态事件发布器（P2-E：打赏后旁路落 social，失败仅记 warn，不阻断支付主流程） */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

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
        // P2-E：打赏动态（AFTER_COMMIT 旁路落 social，失败仅记 warn，不阻断支付主流程）
        publishRewardDynamic(order);
        collectRewardRisk(order.getUserId(), order.getId());
        return order;
    }

    /**
     * 打赏行为风控埋点（P2-C 闭环补全）：支付成功后非阻断采集，风控不可用仅告警。
     */
    private void collectRewardRisk(Long userId, Long orderId) {
        if (behaviorRiskClient == null) {
            return;
        }
        try {
            behaviorRiskClient.collect(userId, null, "REWARD", orderId, null);
        } catch (Exception ignored) {
            // collect 本身已降级；双保险
        }
    }

    /** 订单详情（仅下单人可见） */
    public RewardOrderEntity getOrder(String orderNo, Long userId) {
        RewardOrderEntity order = findByOrderNo(orderNo);
        requireOwner(order, userId);
        return order;
    }

    /** 我的打赏记录（按创建时间倒序），出参补书籍 / 章节标题（16-23，Feign 失败降级为空） */
    public PageResult<RewardOrderEntity> myOrders(Long userId, int page, int size) {
        Page<RewardOrderEntity> p = new Page<>(page, size);
        IPage<RewardOrderEntity> result = rewardOrderMapper.selectPage(p,
                Wrappers.<RewardOrderEntity>lambdaQuery()
                        .eq(RewardOrderEntity::getUserId, userId)
                        .orderByDesc(RewardOrderEntity::getCreateTime));
        enrichTitles(result.getRecords());
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

    /**
     * 稿酬汇总（16-24）：累计收入 + 本月收入（按 settle_month = 当前月过滤）。
     * 聚合在数据库层完成（SUM），避免把全表流水拉到内存求和。
     */
    public IncomeSummary incomeSummary(Long userId) {
        String month = LocalDateTime.now().format(MONTH_FORMATTER);
        IncomeSummary summary = new IncomeSummary();
        summary.setSettleMonth(month);
        summary.setTotalAmount(sumIncome(userId, null));
        summary.setMonthAmount(sumIncome(userId, month));
        return summary;
    }

    /**
     * 录入买断稿酬流水（管理端手工录入，incomeType=4）。
     * 买断无分成比例，全额入账；settleMonth 取运营指定的结算月份 period。
     *
     * @return 已插入的稿酬流水实体
     */
    public AuthorIncomeEntity recordBuyoutIncome(Long authorId, Long bookId, BigDecimal amount, String period) {
        return settleBuyoutIncome(authorId, bookId, amount, period);
    }

    /** 按条件聚合稿酬金额；typeFilter 为空时统计全部类型 */
    private BigDecimal sumIncome(Long userId, String month) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AuthorIncomeEntity> qw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        qw.select("COALESCE(SUM(amount), 0) AS total_amount")
                .eq("author_id", userId);
        if (month != null) {
            qw.eq("settle_month", month);
        }
        List<Object> objs = authorIncomeMapper.selectObjs(qw);
        if (objs.isEmpty() || objs.get(0) == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(objs.get(0).toString()).setScale(2, RoundingMode.HALF_UP);
    }

    /** 批量填充书籍 / 章节标题（16-23）：Feign 不可用或失败时留空，不阻断列表返回 */
    private void enrichTitles(List<RewardOrderEntity> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        for (RewardOrderEntity order : orders) {
            if (order.getBookId() != null && bookClient != null) {
                try {
                    R<BookSummaryDTO> resp = bookClient.getBook(order.getBookId());
                    if (resp != null && resp.getCode() == ResultCode.SUCCESS.getCode() && resp.getData() != null) {
                        order.setBookTitle(resp.getData().getTitle());
                    }
                } catch (Exception ex) {
                    // 降级：标题缺失不影响打赏记录主流程
                }
            }
            if (order.getChapterId() != null && chapterClient != null) {
                try {
                    R<com.moyue.api.content.dto.ChapterDTO> resp = chapterClient.getChapter(order.getChapterId());
                    if (resp != null && resp.getCode() == ResultCode.SUCCESS.getCode() && resp.getData() != null) {
                        order.setChapterTitle(resp.getData().getTitle());
                    }
                } catch (Exception ex) {
                    // 降级：标题缺失不影响打赏记录主流程
                }
            }
        }
    }

    /** 稿酬汇总出参（16-24） */
    public static class IncomeSummary {

        /** 累计稿酬总额 */
        private BigDecimal totalAmount;

        /** 本月稿酬（按 settle_month 过滤） */
        private BigDecimal monthAmount;

        /** 本月标识（yyyy-MM） */
        private String settleMonth;

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public BigDecimal getMonthAmount() {
            return monthAmount;
        }

        public void setMonthAmount(BigDecimal monthAmount) {
            this.monthAmount = monthAmount;
        }

        public String getSettleMonth() {
            return settleMonth;
        }

        public void setSettleMonth(String settleMonth) {
            this.settleMonth = settleMonth;
        }
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

    /**
     * P2-E：发布打赏动态事件（AFTER_COMMIT 旁路落 social）。
     * 经 content BookClient 取作者 / 作品反规范化快照；解析失败（降级）则不发布动态，不影响支付。
     */
    private void publishRewardDynamic(RewardOrderEntity order) {
        BookSummaryDTO book = fetchBookSummary(order.getBookId());
        if (book == null || book.getAuthorId() == null) {
            return;
        }
        try {
            eventPublisher.publishEvent(new RewardDynamicEvent(this, order.getUserId(), book.getAuthorId(),
                    book.getAuthor(), order.getBookId(), book.getTitle(), order.getId()));
        } catch (Exception ex) {
            log.warn("发布打赏动态事件失败 orderId={}, err={}", order.getId(), ex.getMessage());
        }
    }

    /** 经 Feign 取作品摘要（标题 / 作者）；失败或不可用时返回 null（降级，不阻断主流程） */
    private BookSummaryDTO fetchBookSummary(Long bookId) {
        if (bookClient == null || bookId == null) {
            return null;
        }
        try {
            R<BookSummaryDTO> resp = bookClient.getBook(bookId);
            if (resp != null && resp.getCode() == ResultCode.SUCCESS.getCode() && resp.getData() != null) {
                return resp.getData();
            }
        } catch (Exception ex) {
            // 降级：书籍服务不可用，跳过依赖
        }
        return null;
    }

    /** 生成买断稿酬流水：incomeType=4，金额全额入账，settleMonth=period */
    private AuthorIncomeEntity settleBuyoutIncome(Long authorId, Long bookId, BigDecimal amount, String period) {
        if (authorId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "作者 ID 不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "买断稿酬金额必须大于 0");
        }
        if (period == null || period.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "结算月份不能为空");
        }
        AuthorIncomeEntity income = new AuthorIncomeEntity();
        income.setAuthorId(authorId);
        income.setBookId(bookId);
        income.setOrderNo(null);
        income.setIncomeType(INCOME_BUYOUT_SHARE);
        income.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        income.setSettleMonth(period);
        authorIncomeMapper.insert(income);
        return income;
    }

    /** 经 Feign 取书籍作者 ID；失败或非法时返回 null（降级，不阻断支付） */
    private Long resolveAuthorId(Long bookId) {
        BookSummaryDTO book = fetchBookSummary(bookId);
        return book == null ? null : book.getAuthorId();
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
