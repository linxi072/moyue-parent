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

    /** 0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回 */
    private Integer status;

    private LocalDateTime publishTime;
}
