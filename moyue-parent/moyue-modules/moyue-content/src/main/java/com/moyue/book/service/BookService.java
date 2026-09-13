package com.moyue.book.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.search.client.SearchIndexClient;
import com.moyue.api.account.client.UserClient;
import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.api.account.dto.UserDTO;
import com.moyue.book.entity.BookEntity;
import com.moyue.book.mapper.BookMapper;
import com.moyue.common.exception.BizException;
import com.moyue.common.core.domain.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 书城业务（基于 MySQL book 表，替换原内存演示实现）。
 * 通过 Feign UserClient 解析 authorId → 作者昵称；分类用演示字典映射。
 * 写接口鉴权：创建需作者 / 管理员；编辑与删除仅限作者本人或管理员。
 *
 * <p>P2-13 S-3：书籍创建 / 更新 / 删除后经 {@link SearchIndexClient} 同步 ES 索引，
 * Feign 调用失败仅记 warn、不阻断主流程（沿用既有降级风格）。</p>
 */
@Slf4j
@Service
public class BookService {

    /** 演示分类字典：categoryId → 分类名（脚手架占位，生产可独立分类服务） */
    private static final Map<Long, String> CATEGORY_NAMES = new LinkedHashMap<>();
    static {
        CATEGORY_NAMES.put(1L, "玄幻");
        CATEGORY_NAMES.put(2L, "都市");
        CATEGORY_NAMES.put(3L, "悬疑");
    }

    /** 作者角色值 */
    private static final int ROLE_AUTHOR = 2;
    /** 管理员角色值 */
    private static final int ROLE_ADMIN = 3;

    /** 作品状态：已完结（作者不可自行设置，须审核通过） */
    private static final int STATUS_FINISHED = 2;

    @Autowired
    private BookMapper bookMapper;

    @Autowired(required = false)
    private UserClient userClient;

    /** 检索服务 Feign 客户端（P2-13 S-3 索引同步）；search 未注册时安全降级 */
    @Autowired(required = false)
    private SearchIndexClient searchIndexClient;

    @Autowired
    private FileStorage fileStorage;

    /** 分页查询书籍 */
    public PageResult<BookSummaryDTO> listBooks(int page, int size) {
        Page<BookEntity> p = new Page<>(page, size);
        bookMapper.selectPage(p, null);

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

    /** 书籍详情 */
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

    /** 创建作品：authorId 取当前登录用户，初始连载中、字数与点击为 0 */
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
        return toDto(e);
    }

    /** 编辑作品：仅作者本人或管理员；仅更新非空字段 */
    public BookSummaryDTO updateBook(Long bookId, long userId, int role, String title, String coverUrl,
                                     Long categoryId, String tags, String intro, Integer status) {
        BookEntity e = bookMapper.selectById(bookId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        checkOwner(e.getAuthorId(), userId, role);
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
        return toDto(e);
    }

    /**
     * 上传作品封面：作者本人 / 管理员。
     * 落盘与 URL 生成交由 {@link FileStorage}（当前为本地磁盘实现，生产可换 OSS，接口不变）。
     */
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

    /** 删除作品（全局逻辑删除：update is_deleted=1）；仅作者本人或管理员 */
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
            long clickCount = e.getClickCount() == null ? 0L : e.getClickCount();
            long favoriteCount = 0L;
            BookIndexDTO dto = new BookIndexDTO();
            dto.setBookId(e.getId());
            dto.setTitle(e.getTitle());
            dto.setCategoryId(e.getCategoryId());
            dto.setCategoryName(CATEGORY_NAMES.getOrDefault(e.getCategoryId(), "未知"));
            dto.setAuthorName(resolveAuthor(e.getAuthorId()));
            dto.setCoverUrl(e.getCoverUrl());
            dto.setDescription(e.getIntro());
            dto.setStatus(e.getStatus());
            dto.setClickCount(clickCount);
            dto.setFavoriteCount(favoriteCount);
            dto.setHotScore(clickCount + favoriteCount * 3);
            // 以变更时刻作为更新时间，保证 latest 排序反映最新变更
            dto.setUpdateTime(LocalDateTime.now());
            searchIndexClient.indexBook(dto);
        } catch (Exception ex) {
            // 检索服务未注册 / 不可用：安全降级，不阻断书城主流程
            log.warn("同步书籍索引失败 bookId={}, err={}", e.getId(), ex.getMessage());
        }
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
        dto.setCategory(CATEGORY_NAMES.getOrDefault(e.getCategoryId(), "未知"));
        dto.setStatus(e.getStatus());
        dto.setIntro(e.getIntro());
        dto.setAuthor(resolveAuthor(e.getAuthorId()));
        return dto;
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
}
