package com.moyue.read.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.read.entity.BookshelfEntity;
import com.moyue.read.mapper.BookshelfMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 阅读业务：书架查询与增删、阅读进度更新。
 */
@Service
public class ReadService {

    @Autowired
    private BookshelfMapper bookshelfMapper;

    /** 按用户 ID 查询书架（按加入时间倒序） */
    public List<BookshelfEntity> getShelf(Long userId) {
        QueryWrapper<BookshelfEntity> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        qw.orderByDesc("create_time");
        return bookshelfMapper.selectList(qw);
    }

    /**
     * 加入书架：唯一键 uk_user_book 会被逻辑删除行占用，故以「insert，冲突则复活」保证幂等，
     * 避免「收藏 → 取消 → 再收藏」时撞唯一键。
     */
    @Transactional
    public void addToShelf(Long userId, Long bookId) {
        if (bookId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "书籍 ID 不能为空");
        }
        BookshelfEntity e = new BookshelfEntity();
        e.setUserId(userId);
        e.setBookId(bookId);
        e.setIsDeleted(0);
        e.setCreateTime(LocalDateTime.now());
        try {
            bookshelfMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            // 已收藏，或曾取消收藏留下逻辑删除行：复活旧记录，保持幂等
            bookshelfMapper.revive(userId, bookId);
        }
    }

    /** 移出书架（全局逻辑删除） */
    @Transactional
    public void removeFromShelf(Long userId, Long bookId) {
        BookshelfEntity e = findActive(userId, bookId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        bookshelfMapper.deleteById(e.getId());
    }

    /** 更新阅读进度（最后阅读章节） */
    @Transactional
    public void updateProgress(Long userId, Long bookId, Long chapterId) {
        if (chapterId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节 ID 不能为空");
        }
        BookshelfEntity e = findActive(userId, bookId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        e.setLastChapterId(chapterId);
        bookshelfMapper.updateById(e);
    }

    /** 查当前用户在该书上的有效书架行（全局逻辑删除自动过滤已移除行） */
    private BookshelfEntity findActive(Long userId, Long bookId) {
        QueryWrapper<BookshelfEntity> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("book_id", bookId);
        return bookshelfMapper.selectOne(qw);
    }
}
