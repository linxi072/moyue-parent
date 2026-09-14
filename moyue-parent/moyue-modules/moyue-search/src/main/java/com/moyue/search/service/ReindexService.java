package com.moyue.search.service;

import com.moyue.api.ai.client.AiClient;
import com.moyue.api.content.client.BookClient;
import com.moyue.api.content.client.ChapterClient;
import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.api.search.dto.ChapterIndexDTO;
import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import com.moyue.search.document.BookDocument;
import com.moyue.search.document.ChapterDocument;
import com.moyue.search.document.QaDocument;
import com.moyue.search.repository.ChapterSearchRepository;
import com.moyue.search.repository.QaSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端全量重建索引服务：type=book / chapter / qa。
 * 经 Feign 从内容域（书籍 / 章节）与 AI 域（问答）分页拉取全量数据，
 * 逐条覆盖写入对应索引（bookId / chapterId / messageId 为 _id，天然幂等）。
 * 下游降级（fallback 返回 R.code=40002）或网络异常时抛 BizException 中止重建，
 * 已写入部分不受影响（幂等可重跑）。
 */
@Service
public class ReindexService {

    private static final Logger log = LoggerFactory.getLogger(ReindexService.class);

    /** 重建类型：书籍 */
    public static final String TYPE_BOOK = "book";
    /** 重建类型：章节 */
    public static final String TYPE_CHAPTER = "chapter";
    /** 重建类型：AI 客服问答 */
    public static final String TYPE_QA = "qa";

    /** 全量拉取分页大小 */
    private static final int PAGE_SIZE = 100;
    /** 分页护栏：防下游 total 异常导致死循环 */
    private static final int MAX_PAGES = 10000;

    @Autowired
    private BookClient bookClient;

    @Autowired
    private ChapterClient chapterClient;

    @Autowired
    private AiClient aiClient;

    @Autowired
    private SearchService searchService;

    @Autowired
    private ChapterSearchRepository chapterSearchRepository;

    @Autowired
    private QaSearchRepository qaSearchRepository;

    /**
     * 全量重建指定类型索引。
     *
     * @param type book / chapter / qa（非法值抛 PARAM_ERROR）
     * @return 重建写入的文档总数
     */
    public long reindex(String type) {
        return switch (type) {
            case TYPE_BOOK -> reindexBooks();
            case TYPE_CHAPTER -> reindexChapters();
            case TYPE_QA -> reindexQa();
            default -> throw new BizException(ResultCode.PARAM_ERROR,
                    "重建类型非法（仅支持 book / chapter / qa）");
        };
    }

    /** 全量重建书籍索引：分页拉取 BookIndexDTO → BookDocument 覆盖写入 */
    private long reindexBooks() {
        long total = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            R<PageResult<BookIndexDTO>> resp = bookClient.pageBooks(page, PAGE_SIZE);
            List<BookIndexDTO> records = requireRecords(resp, "moyue-content");
            if (records.isEmpty()) {
                break;
            }
            for (BookIndexDTO dto : records) {
                searchService.index(BookDocument.fromIndexDto(dto));
                total++;
            }
            if (records.size() < PAGE_SIZE) {
                break;
            }
        }
        log.info("[reindex] 书籍索引重建完成：{} 条", total);
        return total;
    }

    /** 全量重建章节索引：分页拉取已发布章节（含截断正文）→ ChapterDocument 覆盖写入 */
    private long reindexChapters() {
        long total = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            R<PageResult<ChapterIndexDTO>> resp = chapterClient.pageChapters(page, PAGE_SIZE);
            List<ChapterIndexDTO> records = requireRecords(resp, "moyue-content");
            if (records.isEmpty()) {
                break;
            }
            for (ChapterIndexDTO dto : records) {
                ChapterDocument doc = ChapterDocument.fromIndexDto(dto);
                if (doc != null) {
                    chapterSearchRepository.save(doc);
                    total++;
                }
            }
            if (records.size() < PAGE_SIZE) {
                break;
            }
        }
        log.info("[reindex] 章节索引重建完成：{} 条", total);
        return total;
    }

    /** 全量重建问答索引：分页拉取问答对 → QaDocument 覆盖写入 */
    private long reindexQa() {
        long total = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            R<PageResult<QaIndexDTO>> resp = aiClient.pageQa(page, PAGE_SIZE);
            List<QaIndexDTO> records = requireRecords(resp, "moyue-ai");
            if (records.isEmpty()) {
                break;
            }
            for (QaIndexDTO dto : records) {
                QaDocument doc = QaDocument.fromIndexDto(dto);
                if (doc != null) {
                    qaSearchRepository.save(doc);
                    total++;
                }
            }
            if (records.size() < PAGE_SIZE) {
                break;
            }
        }
        log.info("[reindex] 问答索引重建完成：{} 条", total);
        return total;
    }

    /**
     * 校验下游分页响应：降级（fallback 返回 40002）/ 业务失败 / 无数据 → 抛 BizException 中止重建。
     */
    private <T> List<T> requireRecords(R<PageResult<T>> resp, String service) {
        if (resp == null || resp.getCode() != ResultCode.SUCCESS.getCode() || resp.getData() == null) {
            log.warn("[reindex] 下游 {} 分页拉取失败，中止重建：code={}",
                    service, resp == null ? "无响应" : resp.getCode());
            throw new BizException(ResultCode.PARAM_ERROR, "下游服务 " + service + " 暂不可用，重建中止");
        }
        List<T> records = resp.getData().getRecords();
        return records == null ? List.of() : records;
    }
}
