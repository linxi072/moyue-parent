package com.moyue.search.repository;

import com.moyue.search.document.BookDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 书籍搜索文档 Repository。
 * save 以 bookId 为 _id 覆盖写入（索引同步幂等）；deleteById 用于下架同步。
 */
public interface BookSearchRepository extends ElasticsearchRepository<BookDocument, Long> {
}
