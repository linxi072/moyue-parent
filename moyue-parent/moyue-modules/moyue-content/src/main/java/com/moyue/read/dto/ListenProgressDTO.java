package com.moyue.read.dto;

import lombok.Data;

/**
 * 听书进度 DTO（P2-L 智能朗读断点续听）。
 */
@Data
public class ListenProgressDTO {

    /** 书籍 ID */
    private Long bookId;

    /** 当前收听章节 → chapter.id */
    private Long chapterId;

    /** 章节内片段序号（断点续听） */
    private Integer segmentIndex;

    /** 片段内字符偏移 */
    private Integer charOffset;
}
