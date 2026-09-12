package com.moyue.comment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.dto.PageResult;
import com.moyue.comment.entity.CommentEntity;
import com.moyue.comment.mapper.CommentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 评论业务：按书籍分页查询评论、发表评论（默认待审）。
 */
@Service
public class CommentService {

    @Autowired
    private CommentMapper commentMapper;

    /** 按 book_id 分页查询评论，按创建时间倒序 */
    public PageResult<CommentEntity> listByBook(Long bookId, int page, int size) {
        Page<CommentEntity> p = new Page<>(page, size);
        commentMapper.selectPage(p, Wrappers.<CommentEntity>lambdaQuery()
                .eq(CommentEntity::getBookId, bookId)
                .orderByDesc(CommentEntity::getCreateTime));

        PageResult<CommentEntity> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        result.setRecords(p.getRecords());
        return result;
    }

    /** 发表评论：状态默认 0 待审，点赞数默认 0 */
    public void add(CommentEntity entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(0);
        }
        if (entity.getLikeCount() == null) {
            entity.setLikeCount(0);
        }
        commentMapper.insert(entity);
    }
}
