package com.moyue.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.content.domain.dto.query.BookQuery;
import com.moyue.content.domain.entity.Book;
import com.moyue.content.domain.vo.BookVO;
import com.moyue.content.mapper.BookMapper;
import com.moyue.content.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 作品域实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookMapper bookMapper;

    @Override
    public PageResult<BookVO> pageBooks(BookQuery query) {
        var page = PageUtils.<Book>page(query);
        var result = bookMapper.selectPage(page, new LambdaQueryWrapper<Book>()
                .like(StringUtils.hasText(query.getTitle()), Book::getTitle, query.getTitle())
                .like(StringUtils.hasText(query.getAuthorName()), Book::getAuthorName, query.getAuthorName())
                .eq(query.getStatus() != null, Book::getStatus, query.getStatus())
                .eq(query.getCategoryId() != null, Book::getCategoryId, query.getCategoryId())
                .orderByDesc(Book::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBook(Book entity) {
        if (!StringUtils.hasText(entity.getTitle())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "书名不能为空");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(0);
        }
        if (entity.getWordCount() == null) {
            entity.setWordCount(0L);
        }
        bookMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBook(Book entity) {
        Book exist = bookMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("作品");
        }
        return bookMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBook(Long bookId) {
        Book exist = bookMapper.selectById(bookId);
        if (exist == null) {
            throw BusinessException.notFound("作品");
        }
        return bookMapper.deleteById(bookId) > 0;
    }

    @Override
    public List<BookVO> hotBooks(int limit) {
        int n = Math.max(1, Math.min(limit, 50));
        var list = bookMapper.selectList(new LambdaQueryWrapper<Book>()
                .eq(Book::getStatus, 1)
                .orderByDesc(Book::getWordCount)
                .last("LIMIT " + n));
        return list.stream().map(this::toVO).toList();
    }

    @Override
    public PageResult<BookVO> consumerPageBooks(BookQuery query) {
        // C 端只展示已发布作品（status=1），屏蔽草稿与下架
        var page = PageUtils.<Book>page(query);
        var result = bookMapper.selectPage(page, new LambdaQueryWrapper<Book>()
                .eq(Book::getStatus, 1)
                .like(StringUtils.hasText(query.getTitle()), Book::getTitle, query.getTitle())
                .like(StringUtils.hasText(query.getAuthorName()), Book::getAuthorName, query.getAuthorName())
                .eq(query.getCategoryId() != null, Book::getCategoryId, query.getCategoryId())
                .orderByDesc(Book::getWordCount));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    public BookVO getBook(Long bookId) {
        // selectById 受 @TableLogic 约束，已自动过滤 is_deleted=1 的行
        Book exist = bookMapper.selectById(bookId);
        if (exist == null) {
            throw BusinessException.notFound("作品");
        }
        // C 端不暴露已下架作品（status=2）
        if (exist.getStatus() == null || exist.getStatus() == 2) {
            throw BusinessException.notFound("作品");
        }
        return toVO(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean online(Long bookId) {
        Book exist = bookMapper.selectById(bookId);
        if (exist == null) {
            throw BusinessException.notFound("作品");
        }
        Book upd = new Book();
        upd.setId(bookId);
        upd.setStatus(1);
        return bookMapper.updateById(upd) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean offline(Long bookId) {
        Book exist = bookMapper.selectById(bookId);
        if (exist == null) {
            throw BusinessException.notFound("作品");
        }
        Book upd = new Book();
        upd.setId(bookId);
        upd.setStatus(2);
        return bookMapper.updateById(upd) > 0;
    }

    private BookVO toVO(Book e) {
        return BookVO.builder()
                .id(e.getId())
                .title(e.getTitle())
                .authorName(e.getAuthorName())
                .categoryId(e.getCategoryId())
                .status(e.getStatus())
                .wordCount(e.getWordCount())
                .intro(e.getIntro())
                .coverUrl(e.getCoverUrl())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}
