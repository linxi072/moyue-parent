package com.moyue.author.center;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 单作品看板 VO：继承汇总字段（totalBooks 恒为 1，其余为单书维度聚合），
 * 附加单书基础信息与单书章节统计，供 {@code GET /api/v1/author/center/books/{bookId}/dashboard} 返回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthorBookDashboardVO extends AuthorDashboardVO {

    /** 单书基础信息 */
    private BookBasicInfo book;

    /**
     * 单书基础信息。字段取自 {@code BookSummaryDTO}（author / category 为解析后的字符串）
     * 与 {@code BookEntity}（ratingAvg / ratingCount 仅实体持有，DTO 无此字段），不臆造不存在的字段。
     */
    @Data
    public static class BookBasicInfo {
        private Long bookId;
        private String title;
        /** 作者昵称（经 UserClient 解析） */
        private String author;
        private String coverUrl;
        /** 作品状态：1 连载中 / 2 已完结 / 3 已下架 */
        private Integer status;
        /** 累计字数（源字段类型 Integer，与 BookEntity / BookSummaryDTO 一致） */
        private Integer wordCount;
        /** 累计点击 */
        private Long clickCount;
        /** 评分均值 0.00~5.00 */
        private BigDecimal ratingAvg;
        /** 评分人数 */
        private Integer ratingCount;
        /** 分类名（经 CategoryService 解析） */
        private String category;
    }
}
