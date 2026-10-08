package com.moyue.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.content.domain.dto.query.BookShelfQuery;
import com.moyue.content.domain.entity.BookShelf;
import com.moyue.content.domain.vo.BookShelfVO;
import com.moyue.content.mapper.BookShelfMapper;
import com.moyue.content.mapper.BookMapper;
import com.moyue.content.service.BookShelfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 书架实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookShelfServiceImpl implements BookShelfService {

    private final BookShelfMapper shelfMapper;
    private final BookMapper bookMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addToShelf(Long userId, Long bookId) {
        if (bookMapper.selectById(bookId) == null) {
            throw BusinessException.notFound("作品");
        }
        BookShelf exist = shelfMapper.selectOne(new LambdaQueryWrapper<BookShelf>()
                .eq(BookShelf::getUserId, userId)
                .eq(BookShelf::getBookId, bookId));
        if (exist != null) {
            if (exist.getIsDeleted() != null && exist.getIsDeleted() == 1) {
                // 历史逻辑删除行：复活（保持一行，不新增）
                shelfMapper.revive(userId, bookId);
            }
            // 已存在且在架：幂等，直接返回
            return;
        }
        BookShelf shelf = new BookShelf();
        shelf.setUserId(userId);
        shelf.setBookId(bookId);
        shelf.setLastChapterNo(0);
        shelfMapper.insert(shelf);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFromShelf(Long userId, Long bookId) {
        shelfMapper.delete(new LambdaQueryWrapper<BookShelf>()
                .eq(BookShelf::getUserId, userId)
                .eq(BookShelf::getBookId, bookId));
    }

    @Override
    public PageResult<BookShelfVO> listShelf(Long userId, BookShelfQuery query) {
        // 自定义 join 查询（仅未删除），分页在内存按总量估算后切片
        var all = shelfMapper.selectShelf(userId, query.getBookTitle());
        IPage<BookShelfVO> page = new Page<>(query.getPage(), query.getSize());
        page.setTotal(all.size());
        int from = (int) ((query.getPage() - 1) * query.getSize());
        int to = Math.min(from + (int) query.getSize(), all.size());
        page.setRecords(from >= all.size() ? java.util.List.of() : all.subList(from, to));
        return PageUtils.toResult(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void touch(Long userId, Long bookId, int lastChapterNo) {
        BookShelf exist = shelfMapper.selectOne(new LambdaQueryWrapper<BookShelf>()
                .eq(BookShelf::getUserId, userId)
                .eq(BookShelf::getBookId, bookId)
                .eq(BookShelf::getIsDeleted, 0));
        if (exist == null) {
            return;
        }
        exist.setLastChapterNo(lastChapterNo);
        shelfMapper.updateById(exist);
    }

    @Override
    public PageResult<BookShelfVO> adminPage(BookShelfQuery query) {
        // 管理端全局书架查询（含读者维度），手动分页
        var all = shelfMapper.selectAdminShelf(query.getBookTitle());
        IPage<BookShelfVO> page = new Page<>(query.getPage(), query.getSize());
        page.setTotal(all.size());
        int from = (int) ((query.getPage() - 1) * query.getSize());
        int to = Math.min(from + (int) query.getSize(), all.size());
        page.setRecords(from >= all.size() ? java.util.List.of() : all.subList(from, to));
        return PageUtils.toResult(page);
    }
}
