package com.moyue.paid.service;

import com.moyue.api.member.client.MemberClient;
import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.cache.CacheNames;
import com.moyue.common.cache.LocalCacheTestConfig;
import com.moyue.member.service.MemberService;
import com.moyue.paid.entity.BookSubscriptionEntity;
import com.moyue.paid.entity.ChapterEntitlementEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 付费章节 / 订阅阅读 权益判定集成测试（H2 + @MockBean MemberClient）。
 * 覆盖：免费章恒解锁、付费锁定、单章购买解锁、会员折扣、会员生效解锁、整本订阅解锁、作者本人解锁、幂等。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(LocalCacheTestConfig.class)
public class PaidChapterServiceIntegrationTest {

    private static final long BOOK_ID = 9001L;
    private static final long AUTHOR_ID = 90001L;
    private static final long READER = 123L;
    private static final long OTHER = 456L;
    private static final long SUB = 789L;

    private static final long FREE_CH = 9101L;
    private static final long PAID_CH = 9102L;
    private static final long PAID_CH_DISCOUNT = 9103L;

    @Autowired
    private PaidChapterService paidChapterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private MemberClient memberClient;

    @BeforeEach
    void cleanAndSeed() {
        jdbcTemplate.update("DELETE FROM chapter_entitlement WHERE user_id IN (?,?,?)", READER, OTHER, SUB);
        jdbcTemplate.update("DELETE FROM book_subscription WHERE user_id IN (?,?,?)", READER, OTHER, SUB);
        jdbcTemplate.update("DELETE FROM chapter WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("INSERT INTO book (id, author_id, title, category_id) VALUES (?,?,?,?)",
                BOOK_ID, AUTHOR_ID, "测试作品", 1L);
        jdbcTemplate.update("INSERT INTO chapter (id, book_id, chapter_no, title, content, word_count, status, is_paid, price, free_preview_chars, is_deleted) "
                        + "VALUES (?,?,1,'免费章','免费章全文内容，任何人都能看。',100,2,0,0.00,0,0)",
                FREE_CH, BOOK_ID);
        jdbcTemplate.update("INSERT INTO chapter (id, book_id, chapter_no, title, content, word_count, status, is_paid, price, free_preview_chars, is_deleted) "
                        + "VALUES (?,?,2,'付费章','这是一段需要付费才能阅读的全文内容，前二十字是预览。',100,2,1,10.00,20,0)",
                PAID_CH, BOOK_ID);
        jdbcTemplate.update("INSERT INTO chapter (id, book_id, chapter_no, title, content, word_count, status, is_paid, price, free_preview_chars, is_deleted) "
                        + "VALUES (?,?,3,'折扣章','这是一段需要付费才能阅读的全文内容，前二十字是预览。',100,2,1,10.00,20,0)",
                PAID_CH_DISCOUNT, BOOK_ID);
        // 默认：会员服务降级（不解锁），折扣率 1.00
        when(memberClient.getBenefits(anyLong())).thenReturn(R.fail(ResultCode.SERVICE_DEGRADED));
        when(memberClient.getDiscountRate(anyLong())).thenReturn(R.ok(BigDecimal.ONE));
        if (cacheManager.getCache(CacheNames.PAID_ENTITLEMENT) != null) {
            cacheManager.getCache(CacheNames.PAID_ENTITLEMENT).clear();
        }
    }

    private ChapterEntity paidChapter() {
        ChapterEntity e = new ChapterEntity();
        e.setId(PAID_CH);
        e.setBookId(BOOK_ID);
        e.setIsPaid(1);
        e.setPrice(new BigDecimal("10.00"));
        e.setFreePreviewChars(20);
        e.setContent("这是一段需要付费才能阅读的全文内容，前二十字是预览。");
        return e;
    }

    @Test
    void freeChapter_unlockedForAnonymous() {
        ChapterEntity free = new ChapterEntity();
        free.setId(FREE_CH);
        free.setBookId(BOOK_ID);
        free.setIsPaid(0);
        assertThat(paidChapterService.isUnlocked(null, BOOK_ID, free)).isTrue();
    }

    @Test
    void paidChapter_lockedForAnonymous() {
        assertThat(paidChapterService.isUnlocked(null, BOOK_ID, paidChapter())).isFalse();
    }

    @Test
    void paidChapter_lockedForNonBuyer() {
        assertThat(paidChapterService.isUnlocked(READER, BOOK_ID, paidChapter())).isFalse();
    }

    @Test
    void unlockChapter_grantsAccess_and_idempotent() {
        R<ChapterEntitlementEntity> r = paidChapterService.unlockChapter(READER, PAID_CH);
        assertThat(r.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
        ChapterEntitlementEntity e = r.getData();
        assertThat(e.getStatus()).isEqualTo(1);
        assertThat(e.getAmount()).isEqualByComparingTo("10.00");

        assertThat(paidChapterService.isUnlocked(READER, BOOK_ID, paidChapter())).isTrue();

        // 幂等：重复购买不重复扣费，返回已有记录
        R<ChapterEntitlementEntity> again = paidChapterService.unlockChapter(READER, PAID_CH);
        assertThat(again.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
        assertThat(again.getData().getId()).isEqualTo(e.getId());
    }

    @Test
    void unlockChapter_appliesMemberDiscount() {
        when(memberClient.getDiscountRate(anyLong())).thenReturn(R.ok(new BigDecimal("0.90")));
        R<ChapterEntitlementEntity> r = paidChapterService.unlockChapter(READER, PAID_CH_DISCOUNT);
        assertThat(r.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
        assertThat(r.getData().getAmount()).isEqualByComparingTo("9.00");
    }

    @Test
    void memberActive_unlocksWithoutPurchase() {
        MemberService.MemberBenefits benefits = new MemberService.MemberBenefits();
        benefits.setActive(true);
        when(memberClient.getBenefits(anyLong())).thenReturn(R.ok(benefits));
        assertThat(paidChapterService.isUnlocked(OTHER, BOOK_ID, paidChapter())).isTrue();
    }

    @Test
    void bookSubscription_unlocksAnyChapter() {
        R<BookSubscriptionEntity> r = paidChapterService.subscribeBook(SUB, BOOK_ID, null);
        assertThat(r.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
        assertThat(r.getData().getStatus()).isEqualTo(1);
        assertThat(paidChapterService.isUnlocked(SUB, BOOK_ID, paidChapter())).isTrue();
    }

    @Test
    void authorSelf_alwaysUnlocked() {
        assertThat(paidChapterService.isUnlocked(AUTHOR_ID, BOOK_ID, paidChapter())).isTrue();
    }

    @Test
    void listActiveEntitlements_reflectsPurchase() {
        paidChapterService.unlockChapter(READER, PAID_CH);
        assertThat(paidChapterService.listActiveEntitlements(READER))
                .extracting(ChapterEntitlementEntity::getChapterId)
                .contains(PAID_CH);
    }
}
