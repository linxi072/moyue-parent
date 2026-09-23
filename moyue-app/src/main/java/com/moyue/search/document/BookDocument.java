package com.moyue.search.document;

import com.moyue.api.search.dto.BookIndexDTO;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 书籍搜索文档，映射 Elasticsearch 索引 moyue_book。
 * title / authorName / categoryName / description 均为 Text，可参与全文匹配；
 * 以 bookId 为文档 _id，重复推送即覆盖更新（幂等）。
 *
 * <p>P2-13 由 moyue-content 整包迁入 moyue-search，包名零变更；T04 扩展
 * {@code categoryId}（term 过滤）、{@code clickCount} / {@code favoriteCount} / {@code hotScore}
 * （热度排序）与 {@code updateTime}（latest 排序）字段。</p>
 */
@Data
@Document(indexName = "moyue_book")
public class BookDocument {

    /** 书籍 ID → book.id（作为 ES 文档 _id） */
    @Id
    private Long bookId;

    /** 书名 */
    @Field(type = FieldType.Text)
    private String title;

    /** 作者昵称 */
    @Field(type = FieldType.Text)
    private String authorName;

    /** 分类名 */
    @Field(type = FieldType.Text)
    private String categoryName;

    /** 分类 ID（term 过滤用；P2-13 新增） */
    @Field(type = FieldType.Long)
    private Long categoryId;

    /** 封面图 */
    @Field(type = FieldType.Keyword)
    private String coverUrl;

    /** 简介 */
    @Field(type = FieldType.Text)
    private String description;

    /** 书籍状态：1 连载中 / 2 已完结 / 3 已下架（仅 1/2 参与检索） */
    @Field(type = FieldType.Integer)
    private Integer status;

    /** 点击量（热度实时字段；P2-13 新增） */
    @Field(type = FieldType.Long)
    private long clickCount;

    /** 收藏数（书架收藏数，热度实时字段；P2-13 新增） */
    @Field(type = FieldType.Long)
    private long favoriteCount;

    /** 热度分：clickCount × 1 + favoriteCount × 3（P2-13 新增，hot 排序用） */
    @Field(type = FieldType.Long)
    private long hotScore;

    /** 最近更新时间（P2-13 新增，latest 排序用） */
    @Field(type = FieldType.Date)
    private Date updateTime;

    /**
     * 由跨服务索引载荷构建 ES 文档（bookId 作为 _id，幂等覆盖）。
     *
     * @param dto 内容域推送的书籍索引 DTO；为 {@code null} 时返回 {@code null}
     * @return 可直接落索引的 {@link BookDocument}
     */
    public static BookDocument fromIndexDto(BookIndexDTO dto) {
        if (dto == null) {
            return null;
        }
        BookDocument doc = new BookDocument();
        doc.setBookId(dto.getBookId());
        doc.setTitle(dto.getTitle());
        doc.setAuthorName(dto.getAuthorName());
        doc.setCategoryName(dto.getCategoryName());
        doc.setCategoryId(dto.getCategoryId());
        doc.setCoverUrl(dto.getCoverUrl());
        doc.setDescription(dto.getDescription());
        doc.setStatus(dto.getStatus());
        doc.setClickCount(dto.getClickCount() == null ? 0L : dto.getClickCount());
        doc.setFavoriteCount(dto.getFavoriteCount() == null ? 0L : dto.getFavoriteCount());
        doc.setHotScore(dto.getHotScore() == null ? 0L : dto.getHotScore());
        doc.setUpdateTime(toDate(dto.getUpdateTime()));
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
