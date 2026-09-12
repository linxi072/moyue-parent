package com.moyue.book.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.client.UserClient;
import com.moyue.api.dto.BookSummaryDTO;
import com.moyue.api.dto.PageResult;
import com.moyue.api.dto.UserDTO;
import com.moyue.book.entity.BookEntity;
import com.moyue.book.mapper.BookMapper;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 书城业务（基于 MySQL book 表，替换原内存演示实现）。
 * 通过 Feign UserClient 解析 authorId → 作者昵称；分类用演示字典映射。
 * 写接口鉴权：创建需作者 / 管理员；编辑与删除仅限作者本人或管理员。
 */
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

    @Autowired
    private BookMapper bookMapper;

    @Autowired(required = false)
    private UserClient userClient;

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
            e.setStatus(status);
        }
        bookMapper.updateById(e);
        return toDto(e);
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
