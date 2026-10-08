package com.moyue.content.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节视图（含正文，用于详情/阅读页）。
 *
 * @author moyue
 */
@Data
@Builder
public class ChapterVO {

    private Long id;
    private Long bookId;
    private Integer chapterNo;
    private String title;
    private String content;
    private Integer wordCount;
    private Integer status;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
