package com.moyue.content.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.content.domain.dto.query.BookShelfQuery;
import com.moyue.content.domain.vo.BookShelfVO;

/**
 * 书架服务（读者收藏，幂等）。
 *
 * <p>加入即「在架」，移出即逻辑删除；重新加入复活原行，保证 (user_id, book_id) 唯一。
 *
 * @author moyue
 */
public interface BookShelfService {

    /** 加入书架（幂等：已存在则复活） */
    void addToShelf(Long userId, Long bookId);

    /** 移出书架（逻辑删除） */
    void removeFromShelf(Long userId, Long bookId);

    /** 书架列表（分页） */
    PageResult<BookShelfVO> listShelf(Long userId, BookShelfQuery query);

    /** 更新最近阅读章节号（阅读时同步） */
    void touch(Long userId, Long bookId, int lastChapterNo);

    /** 管理端：全部书架分页（可选作品名模糊，不含已删除） */
    PageResult<BookShelfVO> adminPage(BookShelfQuery query);
}
