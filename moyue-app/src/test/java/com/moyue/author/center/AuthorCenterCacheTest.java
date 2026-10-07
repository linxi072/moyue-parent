package com.moyue.author.center;

import com.moyue.book.service.BookService;
import com.moyue.common.cache.CacheNames;
import com.moyue.common.cache.LocalCacheTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 看板缓存命中 / 失效测试（AUTHOR_DASHBOARD，TTL 5min）。
 * 经 {@link LocalCacheTestConfig} 注入真实本地 CacheManager 使 @Cacheable 生效；
 * 用 {@link SpyBean} 监听 BookService 真实调用次数，证明 overview / bookDashboard 缓存命中与 key 独立。
 *
 * <p>注意：{@link BookService#detail} 自身带 @Cacheable(BOOK_DETAIL)，不能对其做 when/doReturn 桩接
 * （缓存切面会在桩未完成时介入，报 MissingMethodInvocation / UnfinishedStubbing）。本测试改为在 H2 真实
 * seed 一本书（author=1），使 bookDashboard 走真实 detail 链路，再用 verify 断言调用次数。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(LocalCacheTestConfig.class)
class AuthorCenterCacheTest {

    private static final long CACHE_BOOK = 10L;
    private static final long CACHE_AUTHOR = 1L;

    @Autowired
    private AuthorCenterService authorCenterService;

    @Autowired
    private CacheManager cacheManager;

    @SpyBean
    private BookService bookService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSpyAndCache() {
        reset(bookService);
        clearCache(CacheNames.AUTHOR_DASHBOARD);
        clearCache(CacheNames.BOOK_DETAIL);
        // 真实 seed 一本书（author=1），供 bookDashboard 经 detail 走真实链路并命中归属校验
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", CACHE_BOOK);
        jdbcTemplate.update("INSERT INTO book (id,author_id,title,category_id,status,word_count,click_count,rating_avg,rating_count,is_deleted) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?)",
                CACHE_BOOK, CACHE_AUTHOR, "缓存测试书", 1, 1, 100, 0L, BigDecimal.ZERO, 0, 0);
    }

    private void clearCache(String name) {
        Cache c = cacheManager.getCache(name);
        if (c != null) {
            c.clear();
        }
    }

    @AfterEach
    void cleanup() {
        // 自清理：H2 内存库跨测试类复用，移除本类 seed 的 book 10，避免污染后续测试
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", CACHE_BOOK);
        clearCache(CacheNames.AUTHOR_DASHBOARD);
        clearCache(CacheNames.BOOK_DETAIL);
    }

    @Test
    @DisplayName("authorOverview 二次调用命中缓存：listMyBooks 仅 1 次")
    void overview_isCached_acrossCalls() {
        authorCenterService.authorOverview(CACHE_AUTHOR);
        authorCenterService.authorOverview(CACHE_AUTHOR);
        verify(bookService, times(1)).listMyBooks(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("bookDashboard 二次调用命中缓存：detail 仅 1 次")
    void bookDashboard_isCached_acrossCalls() {
        authorCenterService.bookDashboard(CACHE_AUTHOR, CACHE_BOOK);
        authorCenterService.bookDashboard(CACHE_AUTHOR, CACHE_BOOK);
        verify(bookService, times(1)).detail(CACHE_BOOK);
    }

    @Test
    @DisplayName("overview 与 bookDashboard 缓存 key 独立：互不影响命中")
    void overview_and_bookDashboard_keys_independent() {
        authorCenterService.authorOverview(CACHE_AUTHOR);            // key = 1
        authorCenterService.bookDashboard(CACHE_AUTHOR, CACHE_BOOK); // key = 1:10
        authorCenterService.authorOverview(CACHE_AUTHOR);            // 命中，不应再查
        verify(bookService, times(1)).listMyBooks(anyLong(), anyInt(), anyInt());
    }
}
