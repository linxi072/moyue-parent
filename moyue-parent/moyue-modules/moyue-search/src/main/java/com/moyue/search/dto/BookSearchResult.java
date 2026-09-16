package com.moyue.search.dto;

import com.moyue.search.document.BookDocument;
import lombok.Data;

import java.util.List;

/**
 * 书籍检索结果（P1-5 纠错增强）：在分页结果外附带纠错建议词。
 *
 * <p>{@code correctedKeyword} 为 null 表示原始检索词无需纠错；
 * 非 null 表示主检索无命中、已用该词二次召回，前端可提示「你是不是要找：xxx」。</p>
 */
@Data
public class BookSearchResult {

    /** 文档列表 */
    private List<BookDocument> records;

    /** 总命中数 */
    private long total;

    /** 页码（从 1 起） */
    private int page;

    /** 每页大小 */
    private int size;

    /** 纠错建议词（null 表示未纠错） */
    private String correctedKeyword;
}
