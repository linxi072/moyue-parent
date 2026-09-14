package com.moyue.api.search.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 章节索引文档 DTO（跨服务共享）：moyue-content 推送至 moyue-search 的章节索引载荷。
 * 对应 ES 索引 {@code moyue-chapter} 的字段；{@code chapterId} 作为文档 _id，重复推送即覆盖更新（幂等）。
 * 仅「已发布（status=2）」章节参与索引；正文过大时由内容域截断后再推送。
 */
@Data
public class ChapterIndexDTO implements Serializable {

    /** 章节状态：已发布（仅此状态入库索引） */
    public static final int STATUS_PUBLISHED = 2;

    /** 章节 ID → chapter.id（作为 ES 文档 _id，幂等覆盖） */
    private Long chapterId;

    /** 作品 ID → book.id（term 过滤用） */
    private Long bookId;

    /** 作品名（检索结果展示 + 全文匹配辅助） */
    private String bookTitle;

    /** 章节标题（参与全文匹配，权重高于正文） */
    private String chapterTitle;

    /** 章节正文（全文匹配主体；推送前由内容域截断至前 20000 字符） */
    private String content;

    /** 章节状态：0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回（索引仅存 2，供 filter 兜底） */
    private Integer status;

    /** 发布时间 */
    private LocalDateTime publishTime;
}
