package com.moyue.book.service;

import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.book.category.service.CategoryService;
import com.moyue.book.entity.BookEntity;
import com.moyue.book.mapper.BookMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BookService 单测：聚焦 toIndexDto 评分权重计算（P2-13 S-3 热度分 = 点击 + 收藏*3 + round(评分*100)）。
 * 仅 mock 必要依赖（BookMapper / CategoryService）；其余依赖（userClient / searchIndexClient /
 * fileStorage / eventPublisher）在 toIndexDto 路径上不被调用或安全降级，保持最小化 mock 即可跑通。
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookMapper bookMapper;
    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private BookService bookService;

    @Test
    @DisplayName("toIndexDto：热度分 = 点击(10) + 收藏(0)*3 + round(评分4.50*100=450) = 460")
    void toIndexDto_hotScore_weightsClickAndRating() {
        BookEntity e = new BookEntity();
        e.setId(1001L);
        e.setClickCount(10L);
        e.setRatingAvg(new BigDecimal("4.50"));
        e.setRatingCount(3);
        // categoryId / authorId 等其余字段留空：分类解析走 mock（返回 null 不 NPE），
        // 作者解析因 userClient 为 null 安全降级为空串，toIndexDto 不抛异常

        BookIndexDTO dto = bookService.toIndexDto(e);

        long expectedHot = 10L + 0L + Math.round(4.5 * 100); // 460
        assertThat(dto.getHotScore()).isEqualTo(expectedHot);
        assertThat(dto.getRatingAvg()).isEqualTo(4.5);
        assertThat(dto.getRatingCount()).isEqualTo(3);
    }
}
