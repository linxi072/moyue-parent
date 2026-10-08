package com.moyue.content.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.content.domain.dto.query.BookQuery;
import com.moyue.content.domain.entity.Book;
import com.moyue.content.domain.vo.BookVO;

import java.util.List;

/**
 * 作品域服务。
 *
 * @author moyue
 */
public interface BookService {

    PageResult<BookVO> pageBooks(BookQuery query);

    Long createBook(Book entity);

    boolean updateBook(Book entity);

    boolean deleteBook(Long bookId);

    /** 热门作品榜：按字数倒序取前 N（已完结优先） */
    List<BookVO> hotBooks(int limit);

    /** C 端：已发布作品分页（status=1，按热度/更新时间排序） */
    PageResult<BookVO> consumerPageBooks(BookQuery query);

    /** C 端：作品详情（不存在或已下架抛 NOT_FOUND） */
    BookVO getBook(Long bookId);

    /** 作品上架（status → 1，对读者可见） */
    boolean online(Long bookId);

    /** 作品下架（status → 2） */
    boolean offline(Long bookId);
}
