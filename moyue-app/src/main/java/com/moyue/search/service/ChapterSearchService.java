package com.moyue.search.service;

import com.moyue.common.core.domain.PageResult;
import com.moyue.search.config.SearchProperties;
import com.moyue.search.document.ChapterDocument;
import com.moyue.search.dto.ChapterSearchResultDTO;
import com.moyue.search.repository.ChapterSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 章节检索业务（索引 moyue-chapter，IK 分词）：
 * <ul>
 *   <li>index / remove：文档写入与删除（幂等，chapterId 为 _id）；</li>
 *   <li>search：multi_match（chapterTitle^2 + content），查询侧 ik_smart 分词；
 *       status 与可选 bookId 过滤放 bool <b>filter context</b>（不计分且可被 ES 缓存，提效核心）；
 *       content 命中返回高亮片段（{@code <em>} 标签，fragmentSize 150 / noMatchSize 100）。</li>
 * </ul>
 * ES 不可用时连接异常自然抛出，由全局异常处理器统一转 40001。
 */
@Service
public class ChapterSearchService {

    /** 章节状态：已发布（索引内唯一状态，查询层 filter 兜底防同步延迟） */
    private static final int STATUS_PUBLISHED = 2;

    /** 正文高亮片段长度 */
    private static final int HIGHLIGHT_FRAGMENT_SIZE = 150;
    /** 无命中时返回正文的起始字符数 */
    private static final int HIGHLIGHT_NO_MATCH_SIZE = 100;

    @Autowired
    private ChapterSearchRepository chapterSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private SearchProperties searchProperties;

    /** 索引一章（重复推送覆盖更新，幂等） */
    public ChapterDocument index(ChapterDocument doc) {
        return chapterSearchRepository.save(doc);
    }

    /** 章节下架 / 删除同步：物理删除索引文档 */
    public void remove(Long chapterId) {
        chapterSearchRepository.deleteById(chapterId);
    }

    /**
     * 章节检索：chapterTitle（^2）与 content 全文匹配，filter context 过滤 status / bookId，
     * 分页 page 从 1 起；content 高亮片段随 {@link ChapterSearchResultDTO} 返回。
     *
     * @param keyword 关键词（必填）
     * @param bookId  作品 ID（可选，非空时 filter context term 过滤）
     * @param page    页码，从 1 起（小于 1 时按 1 处理）
     * @param size    每页大小（小于等于 0 时取配置默认值）
     * @return 分页检索结果（含高亮片段）
     */
    public PageResult<ChapterSearchResultDTO> search(String keyword, Long bookId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size > 0 ? size : searchProperties.getDefaultPageSize();

        NativeQuery query = NativeQuery.builder()
                // 全文匹配走 query context 参与打分；过滤条件走 filter context：不计分 + 可被 ES 缓存复用
                .withQuery(q -> q.bool(b -> {
                    b.must(m -> m.multiMatch(mm -> mm
                            .query(keyword)
                            .fields("chapterTitle^2", "content")));
                    b.filter(f -> f.term(t -> t.field("status").value(STATUS_PUBLISHED)));
                    if (bookId != null) {
                        b.filter(f -> f.term(t -> t.field("bookId").value(bookId)));
                    }
                    return b;
                }))
                .withHighlightQuery(new HighlightQuery(buildContentHighlight(), ChapterDocument.class))
                .withPageable(PageRequest.of(safePage - 1, safeSize))
                .build();

        SearchHits<ChapterDocument> hits = elasticsearchOperations.search(query, ChapterDocument.class);
        return toPageResult(hits, safePage, safeSize);
    }

    /** 正文高亮配置：{@code <em>} 标签包裹，fragmentSize 150 / 无命中时回传前 100 字符 */
    private Highlight buildContentHighlight() {
        HighlightField contentField = new HighlightField("content", HighlightFieldParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .withFragmentSize(HIGHLIGHT_FRAGMENT_SIZE)
                .withNoMatchSize(HIGHLIGHT_NO_MATCH_SIZE)
                .build());
        return new Highlight(List.of(contentField));
    }

    /** SearchHits → PageResult（含高亮转换） */
    private PageResult<ChapterSearchResultDTO> toPageResult(SearchHits<ChapterDocument> hits, int page, int size) {
        PageResult<ChapterSearchResultDTO> result = new PageResult<>();
        result.setTotal(hits.getTotalHits());
        result.setPage(page);
        result.setSize(size);
        List<ChapterSearchResultDTO> records = new ArrayList<>();
        for (SearchHit<ChapterDocument> hit : hits.getSearchHits()) {
            records.add(ChapterSearchResultDTO.from(hit));
        }
        result.setRecords(records);
        return result;
    }
}
