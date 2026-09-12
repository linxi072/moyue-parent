package com.moyue.search.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 书籍搜索文档，映射 Elasticsearch 索引 moyue_book。
 * title / authorName / categoryName / description 均为 Text，可参与全文匹配；
 * 以 bookId 为文档 _id，重复推送即覆盖更新（幂等）。
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

    /** 封面图 */
    @Field(type = FieldType.Keyword)
    private String coverUrl;

    /** 简介 */
    @Field(type = FieldType.Text)
    private String description;

    /** 书籍状态：1 连载中 / 2 已完结 / 3 已下架（仅 1/2 参与检索） */
    @Field(type = FieldType.Integer)
    private Integer status;
}
