package com.moyue.book.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.search.client.SearchIndexClient;
import com.moyue.api.account.client.UserClient;
import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.api.account.dto.UserDTO;
import com.moyue.book.category.service.CategoryService;
import com.moyue.book.entity.BookEntity;
import com.moyue.book.event.BookDynamicEvent;
import com.moyue.book.mapper.BookMapper;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.common.cache.CacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 书城业务（基于 MySQL book 表，替换原内存演示实现）。
 * 通过 Feign UserClient 解析 authorId → 作者昵称；分类用演示字典映射。
 * 写接口鉴权：创建需作者 / 管理员；编辑与删除仅限作者本人或管理员。
 *
 * <p>P2-13 S-3：书籍创建 / 更新 / 删除后经 {@link SearchIndexClient} 同步 ES 索引，
 * Feign 调用失败仅记 warn、不阻断主流程（沿用既有降级风格）。</p>
 *
 * <p>P2-16 缓存挂载：列表读走 {@link CacheNames#BOOK_LIST}（TTL 5 分钟，key = page:size），
 * 详情读走 {@link CacheNames#BOOK_DETAIL}（TTL 10 分钟，key = bookId）；写路径按
 * 「BOOK_DETAIL 按 bookId 精确失效 + BOOK_LIST allEntries 整表失效」主动删除，
 * 不缓存 null（基建 MoyueCacheAutoConfiguration 已禁 null 值）。</p>
 */
@Slf4j
@Service
public class BookService {

    /**
     * 分类解析已抽离为独立 {@code CategoryService}（P2-A 分类服务独立化）：
     * categoryId → 分类名经 category 表查询，运营后台可增删改排序，替代原硬编码字典。
     */

    /** 作者角色值 */
    private static final int ROLE_AUTHOR = 2;
    /** 管理员角色值 */
    private static final int ROLE_ADMIN = 3;

    /** 作品状态：已完结（作者不可自行设置，须审核通过） */
    private static final int STATUS_FINISHED = 2;

    @Autowired
    private BookMapper bookMapper;

    /** 分类领域服务（P2-A）：categoryId → 分类名经 category 表解析，替代硬编码字典 */
    @Autowired
    private CategoryService categoryService;

    @Autowired(required = false)
    private UserClient userClient;

    /** 检索服务 Feign 客户端（P2-13 S-3 索引同步）；search 未注册时安全降级 */
    @Autowired(required = false)
    private SearchIndexClient searchIndexClient;

    @Autowired
    private FileStorage fileStorage;

    /** 书籍动态事件发布器（P2-E：发布 / 完结旁路落 social，失败仅记 warn，不阻断主流程） */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** 分页查询书籍（P2-16：走 BOOK_LIST 缓存；P2-A 增加 categoryId 过滤） */
    @Cacheable(cacheNames = CacheNames.BOOK_LIST,
            key = "#page + ':' + #size + ':' + (#categoryId == null ? 'all' : #categoryId)")
    public PageResult<BookSummaryDTO> listBooks(int page, int size, Long categoryId) {
        LambdaQueryWrapper<BookEntity> q = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            q.eq(BookEntity::getCategoryId, categoryId);
        }
        Page<BookEntity> p = new Page<>(page, size);
        bookMapper.selectPage(p, q);

        PageResult<BookSummaryDTO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        List<BookSummaryDTO> records = new ArrayList<>();
        for (BookEntity e : p.getRecords()) {
            records.add(toDto(e));
        }
        result.setRecords(records);
        return result;
    }

    /** 书籍详情（P2-16：走 BOOK_DETAIL 缓存，key = bookId，TTL 10 分钟；null 不缓存） */
    @Cacheable(cacheNames = CacheNames.BOOK_DETAIL, key = "#bookId", unless = "#result == null")
    public BookSummaryDTO detail(Long bookId) {
        BookEntity e = bookMapper.selectById(bookId);
        return e == null ? null : toDto(e);
    }

    /**
     * 我的作品：按 authorId 过滤（作者后台「作品管理」页的地基）。
     * 全局逻辑删除生效，已删除作品自动排除。
     */
    public PageResult<BookSummaryDTO> listMyBooks(long userId, int page, int size) {
        Page<BookEntity> p = new Page<>(page, size);
        LambdaQueryWrapper<BookEntity> q = new LambdaQueryWrapper<BookEntity>()
                .eq(BookEntity::getAuthorId, userId)
                .orderByDesc(BookEntity::getId);
        bookMapper.selectPage(p, q);

        PageResult<BookSummaryDTO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        List<BookSummaryDTO> records = new ArrayList<>();
        for (BookEntity e : p.getRecords()) {
            records.add(toDto(e));
        }
        result.setRecords(records);
        return result;
    }

    /** 创建作品：authorId 取当前登录用户，初始连载中、字数与点击为 0（P2-16：新书上架失效列表缓存） */
    @CacheEvict(cacheNames = CacheNames.BOOK_LIST, allEntries = true)
    public BookSummaryDTO createBook(long userId, int role, String title, String coverUrl,
                                     Long categoryId, String tags, String intro) {
        requireAuthor(role);
        if (title == null || title.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "作品名称不能为空");
        }
        if (categoryId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "作品分类不能为空");
        }
        BookEntity e = new BookEntity();
        e.setAuthorId(userId);
        e.setTitle(title.trim());
        e.setCoverUrl(coverUrl);
        e.setCategoryId(categoryId);
        e.setTags(tags);
        e.setIntro(intro);
        e.setStatus(1);
        e.setWordCount(0);
        e.setClickCount(0L);
        e.setIsDeleted(0);
        bookMapper.insert(e);
        BookSummaryDTO dto = toDto(e);
        // P2-E：发布新作动态（type=1，AFTER_COMMIT 旁路落 social，失败仅记 warn，不阻断书城主流程）
        publishBookDynamic(dto.getBookId(), dto.getTitle(), dto.getAuthorId(), dto.getAuthor(), 1);
        return dto;
    }

    /**
     * 编辑作品：仅作者本人或管理员；仅更新非空字段。
     * P2-16：失效该书详情缓存（含上下架状态变化）与列表缓存（列表展示状态/简介等）。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BOOK_DETAIL, key = "#bookId"),
            @CacheEvict(cacheNames = CacheNames.BOOK_LIST, allEntries = true)
    })
    public BookSummaryDTO updateBook(Long bookId, long userId, int role, String title, String coverUrl,
                                     Long categoryId, String tags, String intro, Integer status) {
        BookEntity e = bookMapper.selectById(bookId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkOwner(e.getAuthorId(), userId, role);
        int oldStatus = e.getStatus() == null ? 0 : e.getStatus();
        if (title != null) {
            e.setTitle(title);
        }
        if (coverUrl != null) {
            e.setCoverUrl(coverUrl);
        }
        if (categoryId != null) {
            e.setCategoryId(categoryId);
        }
        if (tags != null) {
            e.setTags(tags);
        }
        if (intro != null) {
            e.setIntro(intro);
        }
        if (status != null) {
            // 完结须走「申请 → 审核通过」流程，作者不能自行把作品置为已完结；管理员（role=3）不受限
            if (role != ROLE_ADMIN && status == STATUS_FINISHED) {
                throw new BizException(ResultCode.PARAM_ERROR, "完结需提交申请并通过审核");
            }
            e.setStatus(status);
        }
        bookMapper.updateById(e);
        BookSummaryDTO dto = toDto(e);
        // P2-E：作品完结动态（status 由非 2 转 2 时发布，AFTER_COMMIT 旁路落 social，失败仅记 warn）
        if (status != null && status == STATUS_FINISHED && oldStatus != STATUS_FINISHED) {
            publishBookDynamic(dto.getBookId(), dto.getTitle(), dto.getAuthorId(), dto.getAuthor(), 2);
        }
        return dto;
    }

    /**
     * 上传作品封面：作者本人 / 管理员。
     * 落盘与 URL 生成交由 {@link FileStorage}（当前为本地磁盘实现，生产可换 OSS，接口不变）。
     * P2-16：封面同时出现在详情与列表，双缓存失效。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BOOK_DETAIL, key = "#bookId"),
            @CacheEvict(cacheNames = CacheNames.BOOK_LIST, allEntries = true)
    })
    public BookSummaryDTO uploadCover(Long bookId, long userId, int role, MultipartFile file) {
        BookEntity e = bookMapper.selectById(bookId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkOwner(e.getAuthorId(), userId, role);
        String url = fileStorage.store(file, "covers");

        BookEntity upd = new BookEntity();
        upd.setId(bookId);
        upd.setCoverUrl(url);
        upd.setUpdateTime(LocalDateTime.now());
        bookMapper.updateById(upd);
        return detail(bookId);
    }

    /**
     * 删除作品（全局逻辑删除：update is_deleted=1）；仅作者本人或管理员。
     * P2-16：失效该书详情缓存与列表缓存。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BOOK_DETAIL, key = "#bookId"),
            @CacheEvict(cacheNames = CacheNames.BOOK_LIST, allEntries = true)
    })
    @Transactional
    public void deleteBook(Long bookId, long userId, int role) {
        BookEntity e = bookMapper.selectById(bookId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkOwner(e.getAuthorId(), userId, role);
        bookMapper.deleteById(bookId);
        removeIndex(bookId);
    }

    /** 写操作需作者或管理员 */
    private void requireAuthor(int role) {
        if (role < ROLE_AUTHOR) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    /** 归属校验：管理员放行，其余须为资源所有者 */
    private void checkOwner(Long ownerId, long userId, int role) {
        if (role != ROLE_ADMIN && !Objects.equals(ownerId, userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * P2-13 S-3：同步书籍索引到 moyue-search（创建 / 更新后调用）。
     *
     * <p>热度分 {@code hotScore = clickCount × 1 + favoriteCount × 3}；内容域暂不持有收藏数，
     * 以 0 兜底。Feign 调用失败仅记 warn，不阻断主流程（沿用既有降级风格）。</p>
     */
    private void syncIndex(BookEntity e) {
        if (searchIndexClient == null || e == null || e.getId() == null) {
            return;
        }
        try {
            searchIndexClient.indexBook(toIndexDto(e));
        } catch (Exception ex) {
            // 检索服务未注册 / 不可用：安全降级，不阻断书城主流程
            log.warn("同步书籍索引失败 bookId={}, err={}", e.getId(), ex.getMessage());
        }
    }

    /**
     * 实体 → 索引载荷 DTO（syncIndex 与管理端全量重建分页拉取复用）。
     * 热度分 {@code hotScore = clickCount × 1 + favoriteCount × 3}；内容域暂不持有收藏数，以 0 兜底。
     */
    public BookIndexDTO toIndexDto(BookEntity e) {
        long clickCount = e.getClickCount() == null ? 0L : e.getClickCount();
        long favoriteCount = 0L;
        BookIndexDTO dto = new BookIndexDTO();
        dto.setBookId(e.getId());
        dto.setTitle(e.getTitle());
        dto.setCategoryId(e.getCategoryId());
        dto.setCategoryName(categoryService.resolveName(e.getCategoryId()));
        dto.setAuthorName(resolveAuthor(e.getAuthorId()));
        dto.setCoverUrl(e.getCoverUrl());
        dto.setDescription(e.getIntro());
        dto.setStatus(e.getStatus());
        dto.setClickCount(clickCount);
        dto.setFavoriteCount(favoriteCount);
        dto.setHotScore(clickCount + favoriteCount * 3);
        // 以变更时刻作为更新时间，保证 latest 排序反映最新变更
        dto.setUpdateTime(LocalDateTime.now());
        return dto;
    }

    /**
     * 分页拉取书籍索引载荷（内部端点专用，不经网关）：按 book.id 升序，
     * 供 moyue-search 管理端全量重建 moyue_book 索引。
     */
    public PageResult<BookIndexDTO> pageForIndex(int page, int size) {
        Page<BookEntity> p = new Page<>(page, size);
        bookMapper.selectPage(p, null);

        PageResult<BookIndexDTO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        List<BookIndexDTO> records = new ArrayList<>();
        for (BookEntity e : p.getRecords()) {
            records.add(toIndexDto(e));
        }
        result.setRecords(records);
        return result;
    }

    /** P2-13 S-3：删除书籍索引（删除后调用）；失败仅记 warn */
    private void removeIndex(Long bookId) {
        if (searchIndexClient == null || bookId == null) {
            return;
        }
        try {
            searchIndexClient.removeBook(bookId);
        } catch (Exception ex) {
            log.warn("删除书籍索引失败 bookId={}, err={}", bookId, ex.getMessage());
        }
    }

    /** 实体 → DTO，并解析作者昵称（Feign 调用失败安全降级为空串） */
    private BookSummaryDTO toDto(BookEntity e) {
        BookSummaryDTO dto = new BookSummaryDTO();
        dto.setBookId(e.getId());
        dto.setAuthorId(e.getAuthorId());
        dto.setTitle(e.getTitle());
        dto.setCoverUrl(e.getCoverUrl());
        dto.setWordCount(e.getWordCount());
        dto.setCategory(categoryService.resolveName(e.getCategoryId()));
        dto.setStatus(e.getStatus());
        dto.setIntro(e.getIntro());
        dto.setAuthor(resolveAuthor(e.getAuthorId()));
        return dto;
    }

    /**
     * P2-A 分类解析对外入口：categoryId → 分类名。
     * 经 {@link CategoryService} 查询 category 表，不再依赖硬编码字典；
     * 解析失败 / 未知分类统一回退为「未知」（见 {@link CategoryService#resolveName}）。
     *
     * @param categoryId 分类主键，可为 null
     * @return 分类名（未知时返回「未知」）
     */
    public String resolveCategoryName(Long categoryId) {
        return categoryService.resolveName(categoryId);
    }

    private String resolveAuthor(Long authorId) {
        if (authorId == null || userClient == null) {
            return "";
        }
        try {
            UserDTO u = userClient.getUser(authorId).getData();
            return u == null ? "" : u.getNickname();
        } catch (Exception ex) {
            // 用户服务未注册 / 不可用：安全降级，不阻断书城查询
            return "";
        }
    }

    /**
     * P2-E：发布书籍动态事件（AFTER_COMMIT 旁路落 social）。
     * 发布本身同步，由 {@link BookDynamicEventListener} 异步于提交后调用 DynamicClient；
     * 任何异常仅记 warn，绝不阻断书城写主流程。
     */
    private void publishBookDynamic(Long bookId, String bookTitle, Long authorId, String authorName, int dynamicType) {
        try {
            eventPublisher.publishEvent(
                    new BookDynamicEvent(this, bookId, bookTitle, authorId, authorName, dynamicType));
        } catch (Exception ex) {
            log.warn("发布书籍动态事件失败 bookId={} type={}, err={}", bookId, dynamicType, ex.getMessage());
        }
    }
}
