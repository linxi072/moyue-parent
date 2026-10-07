package com.moyue.author.center;

import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.book.entity.BookEntity;
import com.moyue.book.mapper.BookMapper;
import com.moyue.book.service.BookService;
import com.moyue.chapter.mapper.ChapterMapper;
import com.moyue.chapter.service.ChapterService;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.common.cache.CacheNames;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.security.SecurityContextHolder;
import com.moyue.operation.service.AuthorIncomeService;
import com.moyue.read.mapper.BookshelfMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 作者创作中心聚合编排核心（P0）。
 *
 * <p>复用既有读能力（{@link BookService#listMyBooks}/{@code detail}、
 * {@link AuthorIncomeService#listByAuthor} 降级返回空）与 {@link ChapterService} 既有写能力
 * （{@code publish}/{@code deleteChapter}/{@code updateChapter} 内部已做归属校验），
 * 仅新增对 bookshelf / chapter 表的纯 COUNT 聚合与看板缓存。</p>
 *
 * <p>看板缓存：{@link CacheNames#AUTHOR_DASHBOARD}，TTL 5 分钟（实时查库兜底）；写路径不主动 evict。</p>
 *
 * <p>偏差说明：{@code BookSummaryDTO} 不持有 ratingAvg / ratingCount，
 * 评分聚合改由 {@link BookMapper} 直接取 {@link BookEntity} 完成（详见架构文档 §9 修正点外的实现偏差）。</p>
 */
@Service
public class AuthorCenterService {

    /** 管理员角色值（单书看板放行） */
    private static final int ROLE_ADMIN = 3;

    /** 章节状态：草稿 */
    private static final int STATUS_DRAFT = 0;
    /** 章节状态：已发布 */
    private static final int STATUS_PUBLISHED = 2;
    /** 章节状态：已驳回 */
    private static final int STATUS_REJECTED = 3;
    /** 章节状态：定时待发布 */
    private static final int STATUS_SCHEDULED = 4;

    /** 我的作品列表单次拉取上限（评分/点击聚合需全量，作者作品数远小于此） */
    private static final int MAX_MY_BOOKS = 10000;

    @Autowired
    private BookService bookService;

    @Autowired
    private BookshelfMapper bookshelfMapper;

    @Autowired
    private ChapterMapper chapterMapper;

    /** 稿酬流水服务（本地 @Service，缺失概率极低；按文档约定 required=false 防御性声明） */
    @Autowired(required = false)
    private AuthorIncomeService authorIncomeService;

    @Autowired
    private BookMapper bookMapper;

    /** 复用既有写能力（内部已做 checkBookOwner + 机审 hook） */
    @Autowired
    private ChapterService chapterService;

    // ------------------------------ 看板聚合（T2） ------------------------------

    /**
     * 汇总看板：跨 book / chapter / bookshelf / author_income 四源聚合当前作者的全作品指标。
     * 缓存名 {@link CacheNames#AUTHOR_DASHBOARD}，key = userId，TTL 5 分钟。
     */
    @Cacheable(cacheNames = CacheNames.AUTHOR_DASHBOARD, key = "#userId")
    public AuthorDashboardVO authorOverview(Long userId) {
        AuthorDashboardVO vo = new AuthorDashboardVO();

        // 1. 我的作品列表（bookId / clickCount / totalBooks）
        PageResult<BookSummaryDTO> mine = bookService.listMyBooks(userId, 1, MAX_MY_BOOKS);
        List<BookSummaryDTO> records = mine.getRecords() == null ? List.of() : mine.getRecords();
        vo.setTotalBooks((int) mine.getTotal());

        long totalClick = 0L;
        List<Long> bookIds = new ArrayList<>();
        for (BookSummaryDTO b : records) {
            if (b.getBookId() != null) {
                bookIds.add(b.getBookId());
            }
            if (b.getClickCount() != null) {
                totalClick += b.getClickCount();
            }
        }
        vo.setTotalClick(totalClick);

        // 2. 全部作品收藏数合计
        vo.setTotalFavorite(bookIds.isEmpty() ? 0L : (long) bookshelfMapper.countByBookIds(bookIds));

        // 3. 章节创作态分布
        vo.setChapterStats(buildChapterStat(bookIds));

        // 4. 稿酬聚合（全量）
        fillIncome(userId, null, vo);

        // 5. 评分加权平均（BookEntity 持有 ratingAvg / ratingCount）
        fillRating(bookIds, vo);

        return vo;
    }

    /**
     * 单作品看板：仅该 bookId 维度的聚合，并附加单书基础信息。
     * 归属校验：作者本人（book.authorId == userId）或管理员（role==3）放行，否则 FORBIDDEN。
     * 缓存名 {@link CacheNames#AUTHOR_DASHBOARD}，key = userId + ':' + bookId，TTL 5 分钟。
     */
    @Cacheable(cacheNames = CacheNames.AUTHOR_DASHBOARD, key = "#userId + ':' + #bookId")
    public AuthorBookDashboardVO bookDashboard(Long userId, Long bookId) {
        if (bookId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "作品 ID 不能为空");
        }
        // 复用 bookService.detail（返回 BookSummaryDTO，含 authorId / author / category / status 等）
        BookSummaryDTO b = bookService.detail(bookId);
        if (b == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        Integer role = SecurityContextHolder.currentRole();
        if (!(Objects.equals(b.getAuthorId(), userId) || (role != null && role == ROLE_ADMIN))) {
            throw new BizException(ResultCode.FORBIDDEN);
        }

        AuthorBookDashboardVO vo = new AuthorBookDashboardVO();
        vo.setTotalBooks(1);
        vo.setTotalClick(b.getClickCount() == null ? 0L : b.getClickCount());
        vo.setTotalFavorite((long) bookshelfMapper.countByBookId(bookId));
        vo.setChapterStats(buildChapterStat(List.of(bookId)));

        // 稿酬聚合（仅该 bookId）
        fillIncome(userId, bookId, vo);

        // 评分（BookEntity 持有）
        BookEntity entity = bookMapper.selectById(bookId);
        BigDecimal ratingAvg = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int ratingCount = 0;
        if (entity != null && entity.getRatingCount() != null && entity.getRatingCount() > 0
                && entity.getRatingAvg() != null) {
            ratingAvg = entity.getRatingAvg().setScale(2, RoundingMode.HALF_UP);
            ratingCount = entity.getRatingCount();
        }
        vo.setRatingAvg(ratingAvg);
        vo.setRatingCount(ratingCount);

        // 单书基础信息：author / category 取自 BookSummaryDTO，ratingAvg / ratingCount 取自 BookEntity
        AuthorBookDashboardVO.BookBasicInfo info = new AuthorBookDashboardVO.BookBasicInfo();
        info.setBookId(b.getBookId());
        info.setTitle(b.getTitle());
        info.setAuthor(b.getAuthor());
        info.setCoverUrl(b.getCoverUrl());
        info.setStatus(b.getStatus());
        info.setWordCount(b.getWordCount());
        info.setClickCount(b.getClickCount());
        info.setCategory(b.getCategory());
        info.setRatingAvg(entity == null ? null : entity.getRatingAvg());
        info.setRatingCount(entity == null ? null : entity.getRatingCount());
        vo.setBook(info);

        return vo;
    }

    /**
     * 稿酬流水（不分页）。投影 {@link AuthorIncomeDTO} → {@link AuthorIncomeVO}；
     * bookId 非 null 时按作品过滤。
     */
    public List<AuthorIncomeVO> listIncome(Long userId, Long bookId) {
        List<AuthorIncomeDTO> incomes = authorIncomeService == null
                ? List.of()
                : authorIncomeService.listByAuthor(userId);
        if (incomes == null || incomes.isEmpty()) {
            return List.of();
        }
        List<AuthorIncomeVO> result = new ArrayList<>();
        for (AuthorIncomeDTO inc : incomes) {
            if (bookId != null && !bookId.equals(inc.getBookId())) {
                continue;
            }
            result.add(toIncomeVo(inc));
        }
        return result;
    }

    // ------------------------------ 批量操作（T4） ------------------------------

    /**
     * 批量删除章节：循环委托 {@link ChapterService#deleteChapter}（内部已做归属校验）。
     * 任一失败收集 failedIds，循环结束后抛 {@link BatchOperationException} 触发整批回滚。
     */
    @Transactional
    public BatchResult batchDeleteChapters(Long userId, Integer role, List<Long> chapterIds) {
        guardIdentity(userId);
        int roleInt = role == null ? 0 : role;
        List<Long> deletedIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        if (chapterIds != null) {
            for (Long id : chapterIds) {
                try {
                    chapterService.deleteChapter(id, userId, roleInt);
                    deletedIds.add(id);
                } catch (BizException ex) {
                    failedIds.add(id);
                }
            }
        }
        if (!failedIds.isEmpty()) {
            throw new BatchOperationException(failedIds);
        }
        return toSuccessResult(deletedIds);
    }

    /**
     * 批量发布 / 定时发布：循环委托 {@link ChapterService#publish}
     * （内部已做归属校验 + 机审 hook，机审 REJECT 抛 CONTENT_BLOCKED）。
     * 任一失败收集 failedIds，循环结束后抛 {@link BatchOperationException} 触发整批回滚。
     *
     * @param publishTime 定时发布时间；null 表示立即发布
     */
    @Transactional
    public BatchResult batchPublishChapters(Long userId, Integer role, List<Long> chapterIds,
                                             LocalDateTime publishTime) {
        guardIdentity(userId);
        int roleInt = role == null ? 0 : role;
        List<Long> publishedIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        if (chapterIds != null) {
            for (Long id : chapterIds) {
                try {
                    chapterService.publish(id, userId, roleInt, publishTime);
                    publishedIds.add(id);
                } catch (BizException ex) {
                    failedIds.add(id);
                }
            }
        }
        if (!failedIds.isEmpty()) {
            throw new BatchOperationException(failedIds);
        }
        return toSuccessResult(publishedIds);
    }

    /**
     * 批量改状态：仅允许 0（草稿）/ 1（提交审核），禁止直置 2/3/4 绕过机审 / 审核。
     * 循环委托 {@link ChapterService#updateChapter}（内部已做归属校验），整批回滚策略同上。
     */
    @Transactional
    public BatchResult batchUpdateStatus(Long userId, Integer role, List<Long> chapterIds, int targetStatus) {
        guardIdentity(userId);
        if (targetStatus != STATUS_DRAFT && targetStatus != 1) {
            throw new BizException(ResultCode.PARAM_ERROR, "targetStatus 仅允许 0(草稿) 或 1(提交审核)");
        }
        int roleInt = role == null ? 0 : role;
        List<Long> updatedIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        if (chapterIds != null) {
            for (Long id : chapterIds) {
                try {
                    chapterService.updateChapter(id, userId, roleInt, null, null, null, targetStatus, null);
                    updatedIds.add(id);
                } catch (BizException ex) {
                    failedIds.add(id);
                }
            }
        }
        if (!failedIds.isEmpty()) {
            throw new BatchOperationException(failedIds);
        }
        return toSuccessResult(updatedIds);
    }

    // ------------------------------ 内部工具 ------------------------------

    /** 身份基础校验：匿名（无 userId）拒绝 */
    private void guardIdentity(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }

    /** 构造章节创作态分布（草稿 / 已发布 / 已驳回 / 定时待发布） */
    private AuthorDashboardVO.ChapterStat buildChapterStat(List<Long> bookIds) {
        AuthorDashboardVO.ChapterStat stat = new AuthorDashboardVO.ChapterStat();
        if (bookIds.isEmpty()) {
            return stat;
        }
        stat.setDraft(chapterMapper.countByBookIdsAndStatus(bookIds, STATUS_DRAFT));
        stat.setPublished(chapterMapper.countByBookIdsAndStatus(bookIds, STATUS_PUBLISHED));
        stat.setRejected(chapterMapper.countByBookIdsAndStatus(bookIds, STATUS_REJECTED));
        stat.setScheduled(chapterMapper.countByBookIdsAndStatus(bookIds, STATUS_SCHEDULED));
        return stat;
    }

    /** 稿酬聚合：totalIncome = Σ amount，monthIncome = 当月 settleMonth 的 Σ amount（元，两位小数） */
    private void fillIncome(Long userId, Long bookId, AuthorDashboardVO vo) {
        List<AuthorIncomeDTO> incomes = authorIncomeService == null
                ? List.of()
                : authorIncomeService.listByAuthor(userId);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal month = BigDecimal.ZERO;
        String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        if (incomes != null) {
            for (AuthorIncomeDTO inc : incomes) {
                if (bookId != null && !bookId.equals(inc.getBookId())) {
                    continue;
                }
                BigDecimal amt = inc.getAmount() == null ? BigDecimal.ZERO : inc.getAmount();
                total = total.add(amt);
                if (currentMonth.equals(inc.getSettleMonth())) {
                    month = month.add(amt);
                }
            }
        }
        vo.setTotalIncome(total.setScale(2, RoundingMode.HALF_UP));
        vo.setMonthIncome(month.setScale(2, RoundingMode.HALF_UP));
    }

    /** 评分加权平均：Σ(ratingAvg × ratingCount) / Σ ratingCount；ratingCount=0/null 跳过，避免除零 */
    private void fillRating(List<Long> bookIds, AuthorDashboardVO vo) {
        BigDecimal ratingSum = BigDecimal.ZERO;
        int ratingCountSum = 0;
        if (!bookIds.isEmpty()) {
            List<BookEntity> books = bookMapper.selectBatchIds(bookIds);
            if (books != null) {
                for (BookEntity b : books) {
                    Integer rc = b.getRatingCount();
                    BigDecimal ra = b.getRatingAvg();
                    if (rc != null && rc > 0 && ra != null) {
                        ratingSum = ratingSum.add(ra.multiply(BigDecimal.valueOf(rc)));
                        ratingCountSum += rc;
                    }
                }
            }
        }
        BigDecimal ratingAvg = ratingCountSum == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : ratingSum.divide(BigDecimal.valueOf(ratingCountSum), 2, RoundingMode.HALF_UP);
        vo.setRatingAvg(ratingAvg);
        vo.setRatingCount(ratingCountSum);
    }

    /** AuthorIncomeDTO → AuthorIncomeVO 投影 */
    private static AuthorIncomeVO toIncomeVo(AuthorIncomeDTO inc) {
        AuthorIncomeVO vo = new AuthorIncomeVO();
        vo.setId(inc.getId());
        vo.setAuthorId(inc.getAuthorId());
        vo.setBookId(inc.getBookId());
        vo.setIncomeType(inc.getIncomeType());
        vo.setAmount(inc.getAmount());
        vo.setSettleMonth(inc.getSettleMonth());
        vo.setSettlementId(inc.getSettlementId());
        vo.setCreateTime(inc.getCreateTime());
        return vo;
    }

    /** 全成功结果构造 */
    private static BatchResult toSuccessResult(List<Long> processedIds) {
        BatchResult result = new BatchResult();
        result.setSuccess(true);
        result.setPublishedIds(processedIds);
        result.setFailedIds(List.of());
        return result;
    }
}
