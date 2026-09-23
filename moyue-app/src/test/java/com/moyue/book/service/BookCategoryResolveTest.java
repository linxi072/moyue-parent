package com.moyue.book.service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BookService 分类解析测试（P2-A 分类服务独立化）。
 * 断言 categoryId → 分类名来自 category 表（V21 种子），不再依赖原 CATEGORY_NAMES 硬编码字典。
 * 通过 {@link BookService#resolveCategoryName(Long)} 直接验证，不触发缓存 / Feign。
 */
@SpringBootTest
@ActiveProfiles("test")
class BookCategoryResolveTest {

    @Autowired
    private BookService bookService;

    @Test
    void resolveCategoryName_usesSeed_notHardcoded() {
        // categoryId=1/2/3 来自 V21 种子：玄幻 / 都市 / 悬疑，不再硬编码
        assertThat(bookService.resolveCategoryName(1L)).isEqualTo("玄幻");
        assertThat(bookService.resolveCategoryName(2L)).isEqualTo("都市");
        assertThat(bookService.resolveCategoryName(3L)).isEqualTo("悬疑");
    }

    @Test
    void resolveCategoryName_unknownFallsBackToUnknown() {
        assertThat(bookService.resolveCategoryName(999999L)).isEqualTo("未知");
        assertThat(bookService.resolveCategoryName(null)).isEqualTo("未知");
    }
}
