package com.moyue.content.domain.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 阅读进度视图。
 *
 * @author moyue
 */
@Data
@Builder
public class ReadProgressVO {

    private Long bookId;
    private Integer chapterNo;
    private Integer position;
    private java.time.LocalDateTime updateTime;
}
