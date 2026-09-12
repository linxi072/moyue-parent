package com.moyue.search.service;

import com.moyue.api.dto.PageResult;
import com.moyue.search.document.BookDocument;
import com.moyue.search.repository.BookSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 书籍检索业务：
 *  - index：文档写入（幂等，bookId 为 _id 覆盖更新）
 *  - remove：下架同步（物理删文档）
 *  - search：title / authorName / categoryName / description 任一命中即返回，
 *    仅返回 status ∈ {1,2}（连载中 / 已完结）的文档，按相关度排序。
 * ES 不可用时连接异常自然抛出，由全局异常处理器统一转 40001。
 */
@Service
public class SearchService {

    /** 参与检索的字段状态白名单：1 连载中 / 2 已完结 */
    private static final int STATUS_ONGOING = 1;
    private static final int STATUS_FINISHED = 2;

    @Autowired
    private BookSearchRepository bookSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    /** 索引一本书（重复推送覆盖更新，幂等） */
    public BookDocument index(BookDocument doc) {
        return bookSearchRepository.save(doc);
    }

    /** 下架同步：物理删除索引文档 */
    public void remove(Long bookId) {
        bookSearchRepository.deleteById(bookId);
    }

    /**
     * 关键词检索：四字段任一 contains 命中；分页 page 从 1 起。
     * 排序走 ES 默认相关度（_score）。
     */
    public PageResult<BookDocument> search(String keyword, int page, int size) {
        Criteria criteria = new Criteria("title").contains(keyword)
                .or(new Criteria("authorName").contains(keyword))
                .or(new Criteria("categoryName").contains(keyword))
                .or(new Criteria("description").contains(keyword))
                // 仅连载中(1) / 已完结(2)参与检索；下架同步延迟时在查询层兜底
                .and(new Criteria("status").in(STATUS_ONGOING, STATUS_FINISHED));

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(Math.max(page - 1, 0), size));

        SearchHits<BookDocument> hits = elasticsearchOperations.search(query, BookDocument.class);

        PageResult<BookDocument> result = new PageResult<>();
        result.setTotal(hits.getTotalHits());
        result.setPage(page);
        result.setSize(size);
        List<BookDocument> records = new ArrayList<>();
        for (SearchHit<BookDocument> hit : hits.getSearchHits()) {
            records.add(hit.getContent());
        }
        result.setRecords(records);
        return result;
    }
}
