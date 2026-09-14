package com.moyue.search.repository;

import com.moyue.search.document.ChapterDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 章节搜索文档 Repository。
 * save 以 chapterId 为 _id 覆盖写入（索引同步幂等）；deleteById 用于章节下架 / 删除同步。
 */
public interface ChapterSearchRepository extends ElasticsearchRepository<ChapterDocument, Long> {
}
