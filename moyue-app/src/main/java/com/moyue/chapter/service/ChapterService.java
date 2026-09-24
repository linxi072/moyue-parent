package com.moyue.chapter.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.content.client.BookClient;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.content.dto.ChapterDTO;
import com.moyue.api.risk.client.RiskClient;
import com.moyue.api.risk.client.BehaviorRiskClient;
import com.moyue.api.risk.dto.ModerationRequestDTO;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.api.search.client.SearchIndexClient;
import com.moyue.api.search.dto.ChapterIndexDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.chapter.mapper.ChapterMapper;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.cache.CacheNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 章节业务：章节详情、目录分页，以及作者写作链路（草稿箱、增删改、定时发布、排序）。
 * 归属校验通过 Feign BookClient 取 book.authorId；客户端不可用时降级为仅角色校验，不阻断创作。
 *
 * <p>P2-15 机审 hook：章节提交（创建）与发布前经 {@link RiskClient} 送 moyue-risk 机审——
 * REJECT 抛 CONTENT_BLOCKED(20002) 阻断落库；REVIEW 照常落库但标记审核中（status=1，
 * audit_task 由 moyue-risk 写入转人工）；机审客户端不可用时安全降级不阻断创作。</p>
 */
@Service
public class ChapterService {

    private static final Logger log = LoggerFactory.getLogger(ChapterService.class);

    private static final int ROLE_AUTHOR = 2;
    private static final int ROLE_ADMIN = 3;

    /** 机审结论：命中拦截级敏感词（RiskClient 契约） */
    private static final String DECISION_REJECT = "REJECT";
    /** 机审结论：命中告警级敏感词，转人工 */
    private static final String DECISION_REVIEW = "REVIEW";

    /** 章节状态：审核中（机审 REVIEW / 定时未到点时承载「转人工」语义） */
    private static final int STATUS_REVIEWING = 1;
    /** 章节状态：已发布 */
    private static final int STATUS_PUBLISHED = 2;
    /**
     * 章节状态：定时待发布（P0-2 新增）。
     * 修正原实现把「定时未到点」也置为 STATUS_REVIEWING(1) 的语义混淆——
     * 若与人工审核同值，定时调度器扫描 status=1 会误激活待人工审核的章节。
     */
    private static final int STATUS_SCHEDULED = 4;

    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired(required = false)
    private BookClient bookClient;

    /** 内容安全服务客户端（P2-15 机审 hook）；risk 未注册时安全降级 */
    @Autowired(required = false)
    private RiskClient riskClient;

    /** 检索服务客户端（章节索引同步 hook）；search 未注册时安全降级 */
    @Autowired(required = false)
    private SearchIndexClient searchIndexClient;

    /** 行为风控客户端（P2-C 收尾）：章节发布成功非阻断埋点 */
    @Autowired(required = false)
    private BehaviorRiskClient behaviorRiskClient;

    /** 章节正文入索引的最大长度：超过部分截断（防单条 ES 文档过大拖垮索引与查询） */
    private static final int CONTENT_INDEX_MAX = 20000;

    /**
     * 按 ID 查询章节（含正文）。
     * 缓存名 {@link CacheNames#CHAPTER_CONTENT}（TTL 30 分钟），key = 章节 ID。
     * 写操作（编辑 / 删除 / 发布 / 排序 / 审核回写）会同时失效该 key 与所属作品的目录缓存。
     */
    @Cacheable(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#id")
    public ChapterEntity getById(Long id) {
        return chapterMapper.selectById(id);
    }

    /**
     * 按作品 ID 查询目录（分页，按章节序号升序）。
     * 缓存名 {@link CacheNames#CHAPTER_CATALOG}（TTL 10 分钟），key = bookId:page:size。
     * 注意：本接口未过滤 status（草稿 / 驳回章节同样返回，与既有行为一致），缓存后最多有 10 分钟延迟。
     */
    @Cacheable(cacheNames = CacheNames.CHAPTER_CATALOG, key = "#bookId + ':' + #page + ':' + #size")
    public PageResult<ChapterEntity> listByBook(Long bookId, int page, int size) {
        Page<ChapterEntity> p = new Page<>(page, size);
        QueryWrapper<ChapterEntity> qw = new QueryWrapper<>();
        qw.eq("book_id", bookId);
        qw.orderByAsc("chapter_no");
        chapterMapper.selectPage(p, qw);

        PageResult<ChapterEntity> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        result.setRecords(p.getRecords());
        return result;
    }

    /** 草稿箱：按作品查 status=0 的章节（分页，序号升序） */
    public PageResult<ChapterEntity> listDrafts(Long bookId, int page, int size) {
        Page<ChapterEntity> p = new Page<>(page, size);
        QueryWrapper<ChapterEntity> qw = new QueryWrapper<>();
        qw.eq("book_id", bookId).eq("status", 0).orderByAsc("chapter_no");
        chapterMapper.selectPage(p, qw);

        PageResult<ChapterEntity> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        result.setRecords(p.getRecords());
        return result;
    }

    /** 创建章节：序号为空则取当前最大序号 +1；wordCount 由正文长度推导 */
    // 新增章节必然改变目录（可能翻页），目录 key 含 page/size 维度无法逐个推导，故整表失效
    @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    @Transactional
    public ChapterEntity createChapter(long userId, int role, Long bookId, String title, String content,
                                       Integer chapterNo, Integer status) {
        requireAuthor(role);
        if (bookId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "作品 ID 不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节正文不能为空");
        }
        checkBookOwner(bookId, userId, role);

        ChapterEntity e = new ChapterEntity();
        e.setBookId(bookId);
        e.setTitle(title.trim());
        e.setContent(content);
        e.setWordCount(content.length());
        e.setChapterNo(chapterNo != null ? chapterNo : nextChapterNo(bookId));
        e.setStatus(status != null ? status : 0);
        e.setIsDeleted(0);
        try {
            chapterMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            // uk_book_no(book_id, chapter_no) 冲突
            throw new BizException(ResultCode.PARAM_ERROR, "章节序号已存在");
        }
        return e;
    }

    /** 编辑章节：仅更新非空字段；正文变更时重算字数 */
    // 章节内容失效按 key；目录受序号/状态变化影响且 key 含 page/size，故整表失效
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#chapterId"),
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    })
    @Transactional
    public ChapterEntity updateChapter(Long chapterId, long userId, int role, String title, String content,
                                       Integer chapterNo, Integer status, LocalDateTime publishTime) {
        ChapterEntity e = chapterMapper.selectById(chapterId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkBookOwner(e.getBookId(), userId, role);
        if (title != null) {
            e.setTitle(title);
        }
        if (content != null) {
            e.setContent(content);
            e.setWordCount(content.length());
        }
        if (chapterNo != null) {
            e.setChapterNo(chapterNo);
        }
        if (status != null) {
            e.setStatus(status);
        }
        if (publishTime != null) {
            e.setPublishTime(publishTime);
        }
        try {
            chapterMapper.updateById(e);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节序号已存在");
        }
        // 索引同步 hook：编辑直达已发布（如审核改发布）时同步章节索引
        syncChapterIndexIfPublished(e);
        return e;
    }

    /** 删除章节（全局逻辑删除：update is_deleted=1） */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#chapterId"),
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    })
    @Transactional
    public void deleteChapter(Long chapterId, long userId, int role) {
        ChapterEntity e = chapterMapper.selectById(chapterId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkBookOwner(e.getBookId(), userId, role);
        chapterMapper.deleteById(chapterId);
        // 索引同步 hook：章节删除 → 物理删除 ES 索引文档
        removeChapterIndex(chapterId);
    }

    /** 发布 / 定时发布：到点时间未到则置 status=1（审核中，待调度器到点转 2） */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#chapterId"),
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    })
    @Transactional
    public ChapterEntity publish(Long chapterId, long userId, int role, LocalDateTime publishTime) {
        ChapterEntity e = chapterMapper.selectById(chapterId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkBookOwner(e.getBookId(), userId, role);
        // P2-15 机审：发布前送审，REJECT 抛 20002 阻断发布
        String decision = moderateOrThrow(chapterId, e.getTitle(), e.getContent());
        LocalDateTime now = LocalDateTime.now();
        if (publishTime == null || !publishTime.isAfter(now)) {
            // 机审 REVIEW（命中告警词转人工）：置审核中由人工裁决，不直接发布
            e.setStatus(DECISION_REVIEW.equals(decision) ? STATUS_REVIEWING : STATUS_PUBLISHED);
            e.setPublishTime(now);
        } else {
            // P0-2：定时发布置「定时待发布(4)」，到点由 ChapterPublishJobHandler 激活为已发布(2)
            e.setStatus(STATUS_SCHEDULED);
            e.setPublishTime(publishTime);
        }
        chapterMapper.updateById(e);
        // 索引同步 hook：仅已发布(2) 入索引；定时待发布(4) 与人工审核中(1) 均不入索引
        syncChapterIndexIfPublished(e);
        // P2-C 收尾：章节实际发布（status=2）时采集 PUBLISH 行为事件，非阻断
        if (Integer.valueOf(STATUS_PUBLISHED).equals(e.getStatus())) {
            collectPublishRisk(userId, e.getId());
        }
        return e;
    }

    /**
     * P0-2：激活到点的定时章节——扫描 status=4 且 publish_time <= now 的章节，
     * 逐条条件更新为已发布(2) 并复用 {@link #syncChapterIndexIfPublished} 同步 ES 索引。
     *
     * <p>幂等：更新条件自带 {@code status=4} 限定，已发布(2) 自然跳过，
     * 重复调度既不重复改状态也不重复建索引；多实例并发下由数据库条件更新保证只成功一次。</p>
     *
     * @return 本次实际激活的章节条数
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CONTENT, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    })
    @Transactional
    public int activateScheduledChapters() {
        LocalDateTime now = LocalDateTime.now();
        List<ChapterEntity> pending = chapterMapper.selectList(Wrappers.<ChapterEntity>lambdaQuery()
                .eq(ChapterEntity::getStatus, STATUS_SCHEDULED)
                .le(ChapterEntity::getPublishTime, now));
        int activated = 0;
        for (ChapterEntity e : pending) {
            int rows = chapterMapper.update(null, Wrappers.<ChapterEntity>lambdaUpdate()
                    .eq(ChapterEntity::getId, e.getId())
                    .eq(ChapterEntity::getStatus, STATUS_SCHEDULED)
                    .set(ChapterEntity::getStatus, STATUS_PUBLISHED));
            if (rows <= 0) {
                // 已被其它实例/上一轮调度激活，跳过，避免重复建索引
                continue;
            }
            e.setStatus(STATUS_PUBLISHED);
            syncChapterIndexIfPublished(e);
            // P2-C 收尾：定时到点转发布亦采集 PUBLISH，行为主体取作者（bookClient 不可用时跳过）
            collectPublishRisk(resolveChapterAuthorId(e.getBookId()), e.getId());
            activated++;
        }
        if (activated > 0) {
            log.info("定时章节激活完成，本次激活 {} 条", activated);
        }
        return activated;
    }

    /** 章节排序：修改序号（uk_book_no 冲突转为参数错误） */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#chapterId"),
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    })
    @Transactional
    public ChapterEntity reorder(Long chapterId, long userId, int role, Integer chapterNo) {
        ChapterEntity e = chapterMapper.selectById(chapterId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkBookOwner(e.getBookId(), userId, role);
        if (chapterNo == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节序号不能为空");
        }
        e.setChapterNo(chapterNo);
        try {
            chapterMapper.updateById(e);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节序号已存在");
        }
        return e;
    }

    /**
     * 审核回写（内部端点专用，不经网关）：status 2=已发布 / 3=已驳回。
     * 由 moyue-audit 审核裁决后经 Feign 调用；置为已发布且无发布时间时补当前时间。
     * 不做归属校验——调用方为受信的内部审核流程。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CONTENT, key = "#chapterId"),
            @CacheEvict(cacheNames = CacheNames.CHAPTER_CATALOG, allEntries = true)
    })
    @Transactional
    public void auditChapter(Long chapterId, Integer status) {
        if (status == null || (status != 2 && status != 3)) {
            throw new BizException(ResultCode.PARAM_ERROR, "审核状态非法（仅支持 2 已发布 / 3 已驳回）");
        }
        ChapterEntity e = chapterMapper.selectById(chapterId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        e.setStatus(status);
        if (status == 2 && e.getPublishTime() == null) {
            e.setPublishTime(LocalDateTime.now());
        }
        chapterMapper.updateById(e);
        // 索引同步 hook：审核通过发布 → 同步章节索引；驳回不入索引
        syncChapterIndexIfPublished(e);
    }

    // ------------------------------ 章节索引同步 hook ------------------------------

    /**
     * 章节已发布时同步索引到 moyue-search（发布 / 审核通过 / 编辑直达已发布后调用）。
     * Feign 调用失败仅记 warn、不阻断主流程（沿用既有降级风格，可经管理端全量重建补偿）。
     */
    private void syncChapterIndexIfPublished(ChapterEntity e) {
        if (searchIndexClient == null || e == null || e.getId() == null
                || !Integer.valueOf(STATUS_PUBLISHED).equals(e.getStatus())) {
            return;
        }
        try {
            searchIndexClient.indexChapter(toIndexDto(e));
        } catch (Exception ex) {
            // 检索服务未注册 / 不可用：安全降级，不阻断章节主流程
            log.warn("同步章节索引失败 chapterId={}, err={}", e.getId(), ex.getMessage());
        }
    }

    /** 章节删除同步：物理删除 ES 索引文档；失败仅记 warn */
    private void removeChapterIndex(Long chapterId) {
        if (searchIndexClient == null || chapterId == null) {
            return;
        }
        try {
            searchIndexClient.removeChapter(chapterId);
        } catch (Exception ex) {
            log.warn("删除章节索引失败 chapterId={}, err={}", chapterId, ex.getMessage());
        }
    }

    /**
     * 实体 → 索引载荷 DTO（索引同步与全量重建分页拉取复用）。
     * 正文超过 {@value #CONTENT_INDEX_MAX} 字符时截断；作品名经 BookClient 解析（不可用时空串兜底）。
     */
    public ChapterIndexDTO toIndexDto(ChapterEntity e) {
        ChapterIndexDTO dto = new ChapterIndexDTO();
        dto.setChapterId(e.getId());
        dto.setBookId(e.getBookId());
        dto.setBookTitle(resolveBookTitle(e.getBookId()));
        dto.setChapterTitle(e.getTitle());
        dto.setContent(truncateForIndex(e.getContent()));
        dto.setStatus(e.getStatus());
        dto.setPublishTime(e.getPublishTime());
        return dto;
    }

    /**
     * 分页拉取已发布章节索引载荷（内部端点专用，不经网关）：按 chapter.id 升序，
     * 供 moyue-search 管理端全量重建 moyue-chapter 索引（含截断后的正文）。
     */
    public PageResult<ChapterIndexDTO> pageForIndex(int page, int size) {
        Page<ChapterEntity> p = new Page<>(page, size);
        QueryWrapper<ChapterEntity> qw = new QueryWrapper<>();
        qw.eq("status", STATUS_PUBLISHED);
        qw.orderByAsc("id");
        chapterMapper.selectPage(p, qw);

        PageResult<ChapterIndexDTO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        List<ChapterIndexDTO> records = new ArrayList<>();
        for (ChapterEntity e : p.getRecords()) {
            records.add(toIndexDto(e));
        }
        result.setRecords(records);
        return result;
    }

    /** 正文截断：超过索引上限（20000 字符）时截取前缀，null 安全 */
    private String truncateForIndex(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > CONTENT_INDEX_MAX ? content.substring(0, CONTENT_INDEX_MAX) : content;
    }

    /** 解析作品名：经 BookClient 取书名；客户端未注册 / 不可用时空串兜底（不阻断索引同步） */
    private String resolveBookTitle(Long bookId) {
        if (bookClient == null || bookId == null) {
            return "";
        }
        try {
            R<BookSummaryDTO> resp = bookClient.getBook(bookId);
            BookSummaryDTO book = resp == null ? null : resp.getData();
            return book == null || book.getTitle() == null ? "" : book.getTitle();
        } catch (Exception ex) {
            // 书籍服务不可用：降级为空作品名，索引同步不受阻
            return "";
        }
    }

    // ------------------------------ P2-C 行为风控埋点 ------------------------------

    /** 章节发布行为风控埋点（非阻断；客户端未就绪 / 无行为主体 / 异常均仅告警跳过） */
    private void collectPublishRisk(Long userId, Long chapterId) {
        if (behaviorRiskClient == null || userId == null) {
            return;
        }
        try {
            behaviorRiskClient.collect(userId, null, "PUBLISH", chapterId, null);
        } catch (Exception ignored) {
            // collect 本身已降级；双保险
        }
    }

    /** 经 BookClient 解析章节作者 ID（行为主体）；客户端未注册 / 不可用 / 异常时返回 null，安全降级 */
    private Long resolveChapterAuthorId(Long bookId) {
        if (bookClient == null || bookId == null) {
            return null;
        }
        try {
            R<BookSummaryDTO> resp = bookClient.getBook(bookId);
            BookSummaryDTO book = resp == null ? null : resp.getData();
            return book == null ? null : book.getAuthorId();
        } catch (Exception ex) {
            return null;
        }
    }

    // ------------------------------ 实体 → DTO 转换 ------------------------------

    /**
     * 单章转换（含正文）。
     * 16-11：Feign {@code ChapterClient#getChapter} 声明返回 {@code R<ChapterDTO>}，
     * 控制器必须回 DTO 而非实体，否则跨服务字段调整会静默漂移。
     */
    public static ChapterDTO toDto(ChapterEntity e) {
        if (e == null) {
            return null;
        }
        ChapterDTO d = new ChapterDTO();
        d.setId(e.getId());
        d.setBookId(e.getBookId());
        d.setChapterNo(e.getChapterNo());
        d.setTitle(e.getTitle());
        d.setWordCount(e.getWordCount());
        d.setContent(e.getContent());
        d.setStatus(e.getStatus());
        d.setPublishTime(e.getPublishTime());
        return d;
    }

    /** 列表转换（不含正文，避免目录接口把整本书正文打进响应体） */
    public static ChapterDTO toDtoLite(ChapterEntity e) {
        if (e == null) {
            return null;
        }
        ChapterDTO d = toDto(e);
        d.setContent(null);
        return d;
    }

    /** 分页转换（列表口径，不含正文） */
    public static PageResult<ChapterDTO> toDtoPage(PageResult<ChapterEntity> src) {
        PageResult<ChapterDTO> r = new PageResult<>();
        r.setTotal(src.getTotal());
        r.setPage(src.getPage());
        r.setSize(src.getSize());
        r.setRecords(src.getRecords() == null
                ? List.of()
                : src.getRecords().stream().map(ChapterService::toDtoLite).toList());
        return r;
    }

    // ------------------------------ 内部工具 ------------------------------

    /**
     * P2-15 机审 hook（落库前）：送 moyue-risk 机审，按结论决定放行 / 阻断。
     * <ul>
     *   <li>REJECT → 抛 CONTENT_BLOCKED(20002)，调用方事务内不落库；</li>
     *   <li>REVIEW → 返回 REVIEW，调用方标记审核中（audit_task 已由 moyue-risk 写入转人工）；</li>
     *   <li>PASS → 返回 PASS；</li>
     *   <li>机审客户端未注册 / 熔断降级（fallback 返回 R.code=40002）/ 调用异常 → 返回 null，
     *       不阻断业务（安全降级约定：内容安全故障不得拖垮创作主链路）。</li>
     * </ul>
     */
    private String moderateOrThrow(Long chapterId, String title, String content) {
        if (riskClient == null) {
            return null;
        }
        try {
            ModerationRequestDTO req = new ModerationRequestDTO();
            req.setBizType(1);
            req.setBizId(chapterId);
            req.setTitle(title);
            req.setContent(content);
            R<ModerationResultDTO> resp = riskClient.moderate(req);
            // fallback 降级 / 业务失败：视为机审不可用，放行并告警
            if (resp == null || resp.getCode() != ResultCode.SUCCESS.getCode() || resp.getData() == null) {
                log.warn("[chapter] 机审客户端降级，跳过机审：chapterId={}, code={}",
                        chapterId, resp == null ? "无响应" : resp.getCode());
                return null;
            }
            String decision = resp.getData().getDecision();
            if (DECISION_REJECT.equals(decision)) {
                throw new BizException(ResultCode.CONTENT_BLOCKED);
            }
            return decision;
        } catch (BizException ex) {
            // 机审拦截属业务结论，原样上抛
            throw ex;
        } catch (Exception ex) {
            log.warn("[chapter] 机审调用异常，跳过机审：chapterId={}, err={}", chapterId, ex.getMessage());
            return null;
        }
    }

    /** 取下一位章节序号 = 当前最大序号 +1（全局逻辑删除会自动过滤已删章节） */
    private int nextChapterNo(Long bookId) {
        QueryWrapper<ChapterEntity> qw = new QueryWrapper<>();
        qw.eq("book_id", bookId).orderByDesc("chapter_no").last("LIMIT 1");
        ChapterEntity last = chapterMapper.selectOne(qw);
        return last == null || last.getChapterNo() == null ? 1 : last.getChapterNo() + 1;
    }

    /** 写操作需作者或管理员 */
    private void requireAuthor(int role) {
        if (role < ROLE_AUTHOR) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 归属校验：管理员放行；否则经 BookClient 取书籍作者比对。
     * 书籍明确不存在（下游返回 RESOURCE_NOT_FOUND）时直接拒绝，避免给不存在的作品写章节；
     * BookClient 不可用（未注册 / 网络异常）时降级为仅角色校验，不阻断作者创作。
     */
    private void checkBookOwner(Long bookId, long userId, int role) {
        if (role == ROLE_ADMIN) {
            return;
        }
        if (bookClient == null) {
            return;
        }
        try {
            R<BookSummaryDTO> resp = bookClient.getBook(bookId);
            // 全局异常处理器以 HTTP 200 + R.code 承载业务错误，Feign 不抛异常，故须显式判码
            if (resp != null && resp.getCode() == ResultCode.RESOURCE_NOT_FOUND.getCode()) {
                throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "作品不存在或已下架");
            }
            BookSummaryDTO book = resp == null ? null : resp.getData();
            if (book != null && book.getAuthorId() != null && !Objects.equals(book.getAuthorId(), userId)) {
                throw new BizException(ResultCode.FORBIDDEN);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            // 书籍服务不可用：降级为仅角色校验
        }
    }
}
