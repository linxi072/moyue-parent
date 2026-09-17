package com.moyue.content.tts.dto;

import lombok.Data;

import java.util.List;

/**
 * 章节朗读结果 DTO。
 */
@Data
public class TtsChapterDTO {

    /** 章节 ID */
    private Long chapterId;

    /** 有序音频片段列表 */
    private List<TtsSegmentDTO> segments;

    /** 预计总时长（秒）；按中文约 5 字/秒 × 语速估算 */
    private int totalDurationSec;
}
