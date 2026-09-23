package com.moyue.content.tts.dto;

import lombok.Data;

/**
 * 朗读片段 DTO。
 */
@Data
public class TtsSegmentDTO {

    /** 片段序号（从 0 开始，有序） */
    private int index;

    /** 片段正文 */
    private String text;

    /** 合成音频（Base64 内联，P0 默认形态；无对象存储依赖） */
    private String audioBase64;

    /** 音频 URL（预留，P1 流式/对象存储接入后填充） */
    private String audioUrl;

    /** 片段在原文本中的起始字符偏移（断点续听对齐用） */
    private int charOffsetStart;

    /** 片段在原文本中的结束字符偏移（不含） */
    private int charOffsetEnd;
}
