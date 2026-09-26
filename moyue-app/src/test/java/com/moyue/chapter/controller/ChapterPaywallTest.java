package com.moyue.chapter.controller;

import com.moyue.api.content.dto.ChapterDTO;
import com.moyue.api.member.client.MemberClient;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.cache.CacheNames;
import com.moyue.common.cache.LocalCacheTestConfig;
import com.moyue.common.security.SecurityContextHolder;
import com.moyue.paid.service.PaidChapterService;
import org.junit.jupiter.api.AfterEach;
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
 * 阅读端付费墙集成测试（经 ChapterController.getChapter 验证全文 / 预览截断）。
 * H2 + @MockBean MemberClient（默认降级为不解锁）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(LocalCacheTestConfig.class)
public class ChapterPaywallTest {

    private static final long BOOK_ID = 9002L;
    private static final long AUTHOR_ID = 90002L;

    private static final String FULL_PAID_CONTENT = "这是一段需要付费才能阅读的全文内容，前二十字是预览。";
    private static final String PAID_PREVIEW = FULL_PAID_CONTENT.substring(0, 20);

    @Autowired
    private ChapterController chapterController;

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
        jdbcTemplate.update("DELETE FROM chapter_entitlement WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book_subscription WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM chapter WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("INSERT INTO book (id, author_id, title, category_id) VALUES (?,?,?,?)",
                BOOK_ID, AUTHOR_ID, "付费墙测试作品", 1L);
        when(memberClient.getBenefits(anyLong())).thenReturn(R.fail(ResultCode.SERVICE_DEGRADED));
        when(memberClient.getDiscountRate(anyLong())).thenReturn(R.ok(BigDecimal.ONE));
        if (cacheManager.getCache(CacheNames.PAID_ENTITLEMENT) != null) {
            cacheManager.getCache(CacheNames.PAID_ENTITLEMENT).clear();
        }
        if (cacheManager.getCache(CacheNames.CHAPTER_CONTENT) != null) {
            cacheManager.getCache(CacheNames.CHAPTER_CONTENT).clear();
        }
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    private long insertPaidChapter(long chapterId) {
        jdbcTemplate.update("INSERT INTO chapter (id, book_id, chapter_no, title, content, word_count, status, is_paid, price, free_preview_chars, is_deleted) "
                        + "VALUES (?,?,1,'付费章','这是一段需要付费才能阅读的全文内容，前二十字是预览。',100,2,1,10.00,20,0)",
                chapterId, BOOK_ID);
        return chapterId;
    }

    private long insertFreeChapter(long chapterId) {
        jdbcTemplate.update("INSERT INTO chapter (id, book_id, chapter_no, title, content, word_count, status, is_paid, price, free_preview_chars, is_deleted) "
                        + "VALUES (?,?,1,'免费章','免费章全文内容，任何人都能看。',100,2,0,0.00,0,0)",
                chapterId, BOOK_ID);
        return chapterId;
    }

    @Test
    void freeChapter_returnsFullContent() {
        long id = insertFreeChapter(9201L);
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(123L, 1));
        R<ChapterDTO> r = chapterController.getChapter(id);
        ChapterDTO dto = r.getData();
        assertThat(dto.getUnlocked()).isTrue();
        assertThat(dto.getContent()).isEqualTo("免费章全文内容，任何人都能看。");
    }

    @Test
    void paidChapter_locked_returnsPreviewOnly() {
        long id = insertPaidChapter(9202L);
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(123L, 1));
        R<ChapterDTO> r = chapterController.getChapter(id);
        ChapterDTO dto = r.getData();
        assertThat(dto.getIsPaid()).isEqualTo(1);
        assertThat(dto.getUnlocked()).isFalse();
        assertThat(dto.getContent()).isEqualTo(PAID_PREVIEW);
        assertThat(dto.getContent().length()).isEqualTo(20);
    }

    @Test
    void paidChapter_afterUnlock_returnsFullContent() {
        long id = insertPaidChapter(9203L);
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(123L, 1));
        paidChapterService.unlockChapter(123L, id);
        R<ChapterDTO> r = chapterController.getChapter(id);
        ChapterDTO dto = r.getData();
        assertThat(dto.getUnlocked()).isTrue();
        assertThat(dto.getContent()).isEqualTo("这是一段需要付费才能阅读的全文内容，前二十字是预览。");
    }

    @Test
    void paidChapter_anonymous_returnsPreviewOnly() {
        long id = insertPaidChapter(9204L);
        SecurityContextHolder.clear();
        R<ChapterDTO> r = chapterController.getChapter(id);
        ChapterDTO dto = r.getData();
        assertThat(dto.getUnlocked()).isFalse();
        assertThat(dto.getContent().length()).isEqualTo(20);
    }
}
