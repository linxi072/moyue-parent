package com.moyue.search.service;

import com.moyue.common.core.domain.PageResult;
import com.moyue.search.config.SearchProperties;
import com.moyue.search.document.QaDocument;
import com.moyue.search.repository.QaSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * AI 客服问答检索业务（索引 moyue-qa，IK 分词；含用户数据，仅供管理端）：
 * <ul>
 *   <li>index / remove：文档写入与单条删除（幂等，messageId 为 _id）；</li>
 *   <li>removeBySession：按会话删除该会话全部问答文档（sessionId term 删除查询）；</li>
 *   <li>search：question + answer multi_match；可选 createTime 时间范围放 bool
 *       <b>filter context</b>（不计分且可被 ES 缓存，提效核心）。</li>
 * </ul>
 * ES 不可用时连接异常自然抛出，由全局异常处理器统一转 40001。
 */
@Service
public class QaSearchService {

    @Autowired
    private QaSearchRepository qaSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private SearchProperties searchProperties;

    /** 索引一轮问答（重复推送覆盖更新，幂等） */
    public QaDocument index(QaDocument doc) {
        return qaSearchRepository.save(doc);
    }

    /** 删除单条问答索引文档 */
    public void remove(Long messageId) {
        qaSearchRepository.deleteById(messageId);
    }

    /** 按会话删除该会话全部问答索引文档（sessionId term 删除查询） */
    public void removeBySession(Long sessionId) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field("sessionId").value(sessionId)))
                .build();
        elasticsearchOperations.delete(query, QaDocument.class);
    }

    /**
     * 问答检索：question / answer 任一 match 命中；可选 createTime 时间范围过滤；
     * 分页 page 从 1 起。时间范围用 CriteriaQuery 表达（Spring Data 转换服务统一处理
     * Date 字段 → epoch millis，规避客户端 DSL 版本差异）。
     *
     * @param keyword   关键词（必填）
     * @param startTime 开始时间（可选，含边界）
     * @param endTime   结束时间（可选，含边界）
     * @param page      页码，从 1 起（小于 1 时按 1 处理）
     * @param size      每页大小（小于等于 0 时取配置默认值）
     * @return 分页检索结果
     */
    public PageResult<QaDocument> search(String keyword, LocalDateTime startTime, LocalDateTime endTime,
                                         int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size > 0 ? size : searchProperties.getDefaultPageSize();

        Criteria criteria = new Criteria("question").matches(keyword)
                .or(new Criteria("answer").matches(keyword));
        if (startTime != null) {
            criteria = criteria.and(new Criteria("createTime").greaterThanEqual(toDate(startTime)));
        }
        if (endTime != null) {
            criteria = criteria.and(new Criteria("createTime").lessThanEqual(toDate(endTime)));
        }

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(safePage - 1, safeSize));

        SearchHits<QaDocument> hits = elasticsearchOperations.search(query, QaDocument.class);
        return toPageResult(hits, safePage, safeSize);
    }

    /** LocalDateTime → java.util.Date（ES Date 字段同款时区约定；Spring Data 转换为 epoch millis 查询） */
    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * RAG 召回：按问题检索 topK 问答片段，供 AI 客服大模型注入参考知识库。
     * <p>复用 {@link #search(String, LocalDateTime, LocalDateTime, int, int)}；问题为空返回空列表。
     * ES 不可用时连接异常自然抛出，由调用方（AiService / Feign 降级）吞掉，不影响对话主流程。</p>
     *
     * @param question 用户问题
     * @return "Q: ...\nA: ..." 片段列表（可空）
     */
    public List<String> retrieveContext(String question) {
        if (question == null || question.isBlank()) {
            return new ArrayList<>();
        }
        PageResult<QaDocument> result = search(question, null, null, 1, searchProperties.getRagTopK());
        List<String> passages = new ArrayList<>();
        if (result.getRecords() != null) {
            for (QaDocument d : result.getRecords()) {
                passages.add("Q: " + (d.getQuestion() == null ? "" : d.getQuestion())
                        + "\nA: " + (d.getAnswer() == null ? "" : d.getAnswer()));
            }
        }
        return passages;
    }

    /** SearchHits → PageResult */
    private PageResult<QaDocument> toPageResult(SearchHits<QaDocument> hits, int page, int size) {
        PageResult<QaDocument> result = new PageResult<>();
        result.setTotal(hits.getTotalHits());
        result.setPage(page);
        result.setSize(size);
        List<QaDocument> records = new ArrayList<>();
        for (SearchHit<QaDocument> hit : hits.getSearchHits()) {
            records.add(hit.getContent());
        }
        result.setRecords(records);
        return result;
    }
}
