package com.moyue.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 章节 DTO（跨服务共享）。
 */
@Data
public class ChapterDTO implements Serializable {

    private Long id;

    private Long bookId;

    private Integer chapterNo;

    private String title;

    private Integer wordCount;

    /**
     * 章节正文。
     * 仅单章查询（GET /chapters/{chapterId}）填充；目录 / 草稿箱等列表接口刻意留空，
     * 避免一次性把整本书正文打进响应体。阅读器请走单章接口取正文。
     */
    private String content;

    /** 0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回 */
    private Integer status;

    private LocalDateTime publishTime;
}
