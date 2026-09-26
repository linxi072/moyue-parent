package com.moyue.paid.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.member.client.MemberClient;
import com.moyue.book.service.BookService;
import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.chapter.service.ChapterService;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.cache.CacheNames;
import com.moyue.member.service.MemberService;
import com.moyue.paid.entity.BookSubscriptionEntity;
import com.moyue.paid.entity.ChapterEntitlementEntity;
import com.moyue.paid.mapper.BookSubscriptionMapper;
import com.moyue.paid.mapper.ChapterEntitlementMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 付费章节 / 订阅阅读业务。
 *
 * <p>纯后端契约实现，不接真实支付网关：购买即「契约下单」，记录立即标记已解锁（channel=stub）。
 * 金额统一 {@code BigDecimal}（元，2 位 {@code HALF_UP}）；会员折扣经 {@link MemberClient#getDiscountRate} 复用。</p>
 *
 * <p>解锁判定（阅读付费墙核心）：作者本人 / 章节免费 / 已购本章 / 整本订阅生效中 / 会员生效中 → 解锁；
 * 否则锁定的付费章仅返回前 {@code freePreviewChars} 字预览。会员服务不可用时安全降级为「不解锁」（不阻断阅读主链路）。</p>
 */
@Slf4j
@Service
public class PaidChapterService {

    private static final String CHANNEL_STUB = "stub";

    @Autowired
    private ChapterService chapterService;

    @Autowired
    private BookService bookService;

    @Autowired
    private ChapterEntitlementMapper chapterEntitlementMapper;

    @Autowired
    private BookSubscriptionMapper bookSubscriptionMapper;

    /** 会员服务客户端（可选依赖）：不可用时解锁判定中相关路径安全降级 */
    @Autowired(required = false)
    private MemberClient memberClient;

    /**
     * 判定章节是否对当前用户解锁。
     *
     * @param userId  当前用户（匿名传 null）
     * @param bookId  作品 ID
     * @param chapter 章节实体（已含 isPaid / price / freePreviewChars）
     * @return 是否解锁（免费章恒为 true）
     */
    public boolean isUnlocked(Long userId, Long bookId, ChapterEntity chapter) {
        if (chapter == null) {
            return false;
        }
        // 免费章：无需权益，直接解锁
        if (chapter.getIsPaid() == null || chapter.getIsPaid() == 0) {
            return true;
        }
        // 匿名用户读付费章：锁定
        if (userId == null) {
            return false;
        }
        // 作者本人：始终解锁
        try {
            BookSummaryDTO book = bookService.detail(bookId);
            if (book != null && book.getAuthorId() != null && book.getAuthorId().equals(userId)) {
                return true;
            }
        } catch (Exception ignored) {
            // 书籍服务不可用：跳过作者判定，继续走购买/订阅/会员路径
        }
        // 已购单章：解锁
        if (chapterEntitlementMapper.selectActive(userId, chapter.getId()) != null) {
            return true;
        }
        // 整本订阅生效中：解锁
        if (bookSubscriptionMapper.selectActive(userId, bookId) != null) {
            return true;
        }
        // 会员生效中（复用全局会员权益）：解锁；会员服务不可用降级为不解锁
        if (memberClient != null) {
            try {
                R<MemberService.MemberBenefits> r = memberClient.getBenefits(userId);
                if (r != null && r.getData() != null && r.getData().isActive()) {
                    return true;
                }
            } catch (Exception ignored) {
                // 会员服务降级：不解锁
            }
        }
        return false;
    }

    /**
     * 生成付费章预览片段：未解锁时阅读端截断返回前 {@code freePreviewChars} 字。
     */
    public String previewOf(ChapterEntity chapter) {
        if (chapter == null || chapter.getContent() == null) {
            return "";
        }
        Integer n = chapter.getFreePreviewChars();
        if (n == null || n <= 0 || chapter.getContent().length() <= n) {
            return chapter.getContent();
        }
        return chapter.getContent().substring(0, n);
    }

    /**
     * 解锁（购买）单章。纯后端契约：创建记录并立即标记已解锁（channel=stub），不接真实支付网关。
     * <p>幂等：已解锁直接返回原记录，不重复扣费。购买后清除该用户权益缓存。</p>
     */
    @CacheEvict(cacheNames = CacheNames.PAID_ENTITLEMENT, key = "#userId")
    @Transactional
    public R<ChapterEntitlementEntity> unlockChapter(Long userId, Long chapterId) {
        ChapterEntity chapter = chapterService.getById(chapterId);
        if (chapter == null) {
            return R.fail(ResultCode.RESOURCE_NOT_FOUND);
        }
        // 免费章无需购买
        if (chapter.getIsPaid() == null || chapter.getIsPaid() == 0) {
            return R.ok(null);
        }
        // 幂等：已解锁不重复扣费
        ChapterEntitlementEntity existing = chapterEntitlementMapper.selectActive(userId, chapterId);
        if (existing != null) {
            return R.ok(existing);
        }
        BigDecimal price = chapter.getPrice() != null ? chapter.getPrice() : BigDecimal.ZERO;
        BigDecimal rate = BigDecimal.ONE;
        if (memberClient != null) {
            try {
                R<BigDecimal> dr = memberClient.getDiscountRate(userId);
                if (dr != null && dr.getData() != null) {
                    rate = dr.getData();
                }
            } catch (Exception ignored) {
                // 会员折扣服务降级：原价购买
            }
        }
        BigDecimal amount = price.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        ChapterEntitlementEntity e = new ChapterEntitlementEntity();
        e.setUserId(userId);
        e.setChapterId(chapterId);
        e.setBookId(chapter.getBookId());
        e.setOrderNo(generateOrderNo());
        e.setAmount(amount);
        e.setChannel(CHANNEL_STUB);
        e.setStatus(1);
        e.setExpireTime(null);
        e.setIsDeleted(0);
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        chapterEntitlementMapper.insert(e);
        return R.ok(e);
    }

    /**
     * 整本订阅。纯后端契约：创建记录并立即生效（channel=stub），有效期默认 30 天。
     * <p>幂等：已有生效中订阅直接返回原记录。</p>
     */
    @CacheEvict(cacheNames = CacheNames.PAID_ENTITLEMENT, key = "#userId")
    @Transactional
    public R<BookSubscriptionEntity> subscribeBook(Long userId, Long bookId, BigDecimal amount) {
        BookSubscriptionEntity existing = bookSubscriptionMapper.selectActive(userId, bookId);
        if (existing != null) {
            return R.ok(existing);
        }
        BigDecimal payAmount = (amount != null ? amount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        LocalDateTime now = LocalDateTime.now();
        BookSubscriptionEntity e = new BookSubscriptionEntity();
        e.setUserId(userId);
        e.setBookId(bookId);
        e.setOrderNo(generateOrderNo());
        e.setAmount(payAmount);
        e.setChannel(CHANNEL_STUB);
        e.setStatus(1);
        e.setStartTime(now);
        e.setEndTime(now.plusDays(30));
        e.setIsDeleted(0);
        e.setCreateTime(now);
        e.setUpdateTime(now);
        bookSubscriptionMapper.insert(e);
        return R.ok(e);
    }

    /**
     * 当前用户已解锁清单（按 userId 维度缓存）。仅供「我的权益」列表使用，不影响单章读取判定。
     */
    @Cacheable(cacheNames = CacheNames.PAID_ENTITLEMENT, key = "#userId", unless = "#result.isEmpty()")
    public List<ChapterEntitlementEntity> listActiveEntitlements(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return chapterEntitlementMapper.selectList(Wrappers.<ChapterEntitlementEntity>lambdaQuery()
                .eq(ChapterEntitlementEntity::getUserId, userId)
                .eq(ChapterEntitlementEntity::getStatus, 1)
                .eq(ChapterEntitlementEntity::getIsDeleted, 0));
    }

    private String generateOrderNo() {
        return "PAY" + IdWorker.getId();
    }
}
