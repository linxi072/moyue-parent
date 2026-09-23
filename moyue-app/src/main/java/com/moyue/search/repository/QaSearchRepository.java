package com.moyue.search.repository;

import com.moyue.search.document.QaDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * AI 客服问答搜索文档 Repository。
 * save 以 messageId 为 _id 覆盖写入（索引同步幂等）；deleteById 用于单条删除；
 * 按会话删除走 QaSearchService 的 deleteBy 查询（sessionId term）。
 */
public interface QaSearchRepository extends ElasticsearchRepository<QaDocument, Long> {
}
