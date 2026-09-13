package com.moyue.api.content.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 书籍概要 DTO（跨服务共享，贴合小说站字段）。
 */
@Data
public class BookSummaryDTO implements Serializable {

    private Long bookId;

    private Long authorId;

    private String title;

    private String author;

    private Integer wordCount;

    private String category;

    /** 1 连载中 / 2 已完结 / 3 已下架 */
    private Integer status;

    private String coverUrl;

    private String intro;

    /** 点击量（P2-13 检索热度字段，向后兼容追加于末尾） */
    private Long clickCount;
}
