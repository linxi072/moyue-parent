package com.moyue.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 书籍索引文档 DTO（跨服务共享）：moyue-content 推送至 moyue-search 的索引载荷。
 * 对应 ES 索引 {@code moyue_book} 的字段；{@code hotScore} 由内容域计算后随本 DTO 下发
 * （{@code hotScore = clickCount × 1 + favoriteCount × 3}）。
 */
@Data
public class BookIndexDTO implements Serializable {

    /** 书籍 ID → book.id（作为 ES 文档 _id，幂等覆盖） */
    private Long bookId;

    /** 书名 */
    private String title;

    /** 分类 ID（检索过滤用） */
    private Long categoryId;

    /** 分类名 */
    private String categoryName;

    /** 作者昵称 */
    private String authorName;

    /** 封面图 */
    private String coverUrl;

    /** 简介 */
    private String description;

    /** 书籍状态：1 连载中 / 2 已完结 / 3 已下架（仅 1/2 参与检索） */
    private Integer status;

    /** 点击量（热度实时字段） */
    private Long clickCount;

    /** 收藏数（书架收藏数，热度实时字段） */
    private Long favoriteCount;

    /** 热度分：clickCount × 1 + favoriteCount × 3 */
    private Long hotScore;

    /** 最近更新时间（latest 排序用） */
    private LocalDateTime updateTime;
}
