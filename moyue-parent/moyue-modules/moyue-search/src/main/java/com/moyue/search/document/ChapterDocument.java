package com.moyue.search.document;

import com.moyue.api.search.dto.ChapterIndexDTO;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 章节搜索文档，映射 Elasticsearch 索引 moyue-chapter。
 * 索引 / 查询分词均为 IK：写入 ik_max_word（细粒度切词提升召回）、查询 ik_smart（粗粒度避免过度切词）；
 * 仅「已发布（status=2）」章节入库；以 chapterId 为文档 _id，重复推送即覆盖更新（幂等）。
 *
 * <p>bookTitle 为 Text + keyword 子字段（bookTitle.keyword 可做 term 聚合）；
 * chapterTitle 权重高于 content（multi_match chapterTitle^2），content 承接正文长文本全文匹配。</p>
 */
@Data
@Document(indexName = "moyue-chapter")
public class ChapterDocument {

    /** 章节 ID → chapter.id（作为 ES 文档 _id，幂等覆盖） */
    @Id
    private Long chapterId;

    /** 作品 ID → book.id（keyword，filter context term 过滤用，不计分可缓存） */
    @Field(type = FieldType.Keyword)
    private Long bookId;

    /** 作品名：Text（IK）+ keyword 子字段 */
    @MultiField(mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart"),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword))
    private String bookTitle;

    /** 章节标题：IK 分词，multi_match 中权重 ^2（标题命中相关性高于正文） */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String chapterTitle;

    /** 章节正文：IK 分词，全文匹配主体；入库前已由内容域截断至前 20000 字符 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    /** 章节状态：0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回（仅 2 入库，查询层 filter 兜底） */
    @Field(type = FieldType.Integer)
    private Integer status;

    /** 发布时间 */
    @Field(type = FieldType.Date)
    private Date publishTime;

    /**
     * 由跨服务索引载荷构建 ES 文档（chapterId 作为 _id，幂等覆盖）。
     *
     * @param dto 内容域推送的章节索引 DTO；为 {@code null} 时返回 {@code null}
     * @return 可直接落索引的 {@link ChapterDocument}
     */
    public static ChapterDocument fromIndexDto(ChapterIndexDTO dto) {
        if (dto == null) {
            return null;
        }
        ChapterDocument doc = new ChapterDocument();
        doc.setChapterId(dto.getChapterId());
        doc.setBookId(dto.getBookId());
        doc.setBookTitle(dto.getBookTitle());
        doc.setChapterTitle(dto.getChapterTitle());
        doc.setContent(dto.getContent());
        doc.setStatus(dto.getStatus() == null ? ChapterIndexDTO.STATUS_PUBLISHED : dto.getStatus());
        doc.setPublishTime(toDate(dto.getPublishTime()));
        return doc;
    }

    /** LocalDateTime → java.util.Date（ES Date 字段类型要求），使用系统默认时区转换 */
    private static Date toDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
