package com.moyue.book.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.client.UserClient;
import com.moyue.api.dto.BookSummaryDTO;
import com.moyue.api.dto.PageResult;
import com.moyue.api.dto.UserDTO;
import com.moyue.book.entity.BookEntity;
import com.moyue.book.mapper.BookMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 书城业务（基于 MySQL book 表，替换原内存演示实现）。
 * 通过 Feign UserClient 解析 authorId → 作者昵称；分类用演示字典映射。
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
