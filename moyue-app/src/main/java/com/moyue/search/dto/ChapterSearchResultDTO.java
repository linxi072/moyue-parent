package com.moyue.search.dto;

import com.moyue.search.document.ChapterDocument;
import lombok.Data;
import org.springframework.data.elasticsearch.core.SearchHit;

import java.util.Date;
import java.util.List;

/**
 * 章节检索结果 DTO：章节元信息 + 正文命中高亮片段（{@code <em>} 标签包裹）。
 * 正文高亮来自 ES highlight（fragmentSize 150 / noMatchSize 100）。
 */
@Data
public class ChapterSearchResultDTO {

    /** 章节 ID */
    private Long chapterId;

    /** 作品 ID */
    private Long bookId;

    /** 作品名 */
    private String bookTitle;

    /** 章节标题 */
    private String chapterTitle;

    /** 章节状态（恒为 2 已发布） */
    private Integer status;

    /** 发布时间 */
    private Date publishTime;

    /** 正文命中高亮片段列表（可能为空：仅标题命中时无正文高亮） */
    private List<String> highlights;

    /**
     * 由 ES 命中构建结果 DTO（元信息 + 正文高亮片段）。
     *
     * @param hit ES 单条命中（含高亮）
     * @return 可直接下发的检索结果
     */
    public static ChapterSearchResultDTO from(SearchHit<ChapterDocument> hit) {
        ChapterDocument doc = hit.getContent();
        ChapterSearchResultDTO dto = new ChapterSearchResultDTO();
        dto.setChapterId(doc.getChapterId());
        dto.setBookId(doc.getBookId());
        dto.setBookTitle(doc.getBookTitle());
        dto.setChapterTitle(doc.getChapterTitle());
        dto.setStatus(doc.getStatus());
        dto.setPublishTime(doc.getPublishTime());
        dto.setHighlights(hit.getHighlightField("content"));
        return dto;
    }
}
