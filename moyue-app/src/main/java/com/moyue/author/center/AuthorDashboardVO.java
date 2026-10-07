package com.moyue.author.center;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 作者创作中心汇总看板 VO（全作品累计维度）。
 * 聚合 book / chapter / bookshelf / author_income 四源，供 {@code GET /api/v1/author/center/overview} 返回。
 */
@Data
public class AuthorDashboardVO {

    /** 作品总数 */
    private int totalBooks;

    /** 全部作品点击量合计 */
    private long totalClick;

    /** 全部作品收藏数合计（书架表聚合） */
    private long totalFavorite;

    /** 稿酬总收入（元，保留两位小数） */
    private BigDecimal totalIncome;

    /** 当月稿酬收入（元，保留两位小数） */
    private BigDecimal monthIncome;

    /** 章节创作态分布（草稿 / 已发布 / 已驳回 / 定时待发布） */
    private ChapterStat chapterStats;

    /** 全部作品评分加权平均 */
    private BigDecimal ratingAvg;

    /** 全部作品评分人数合计 */
    private int ratingCount;

    /** 章节创作态分布计数 */
    @Data
    public static class ChapterStat {
        /** 草稿（status=0） */
        private long draft;
        /** 已发布（status=2） */
        private long published;
        /** 已驳回（status=3） */
        private long rejected;
        /** 定时待发布（status=4） */
        private long scheduled;
    }
}
