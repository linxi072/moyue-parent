package com.moyue.comment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.dto.PageResult;
import com.moyue.comment.entity.CommentEntity;
import com.moyue.comment.entity.CommentLikeEntity;
import com.moyue.comment.mapper.CommentLikeMapper;
import com.moyue.comment.mapper.CommentMapper;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 评论业务：按书籍分页查询评论、发表评论（默认待审）、删除（本人 / 管理员）、点赞切换。
 * 发表评论的 userId 一律由调用方从网关注入头取得，绝不信任请求体。
 */
@Service
public class CommentService {

    private static final int ROLE_ADMIN = 3;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private CommentLikeMapper commentLikeMapper;

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

    /** 发表评论：userId 取当前登录用户；状态默认 0 待审，点赞数默认 0 */
    public CommentEntity addComment(Long userId, Long bookId, Long chapterId, String content) {
        if (bookId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "作品 ID 不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "评论内容不能为空");
        }
        CommentEntity e = new CommentEntity();
        e.setUserId(userId);
        e.setBookId(bookId);
        e.setChapterId(chapterId);
        e.setContent(content);
        e.setStatus(0);
        e.setLikeCount(0);
        e.setIsDeleted(0);
        commentMapper.insert(e);
        return e;
    }

    /** 删除评论（逻辑删除）：仅评论人本人或管理员 */
    @Transactional
    public void deleteComment(Long commentId, long userId, int role) {
        CommentEntity e = commentMapper.selectById(commentId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (role != ROLE_ADMIN && !Objects.equals(e.getUserId(), userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        commentMapper.deleteById(commentId);
    }

    /**
     * 点赞切换：已点赞则取消（逻辑删除点赞记录 + like_count-1），否则点赞（防重复 + like_count+1）。
     * 计数用数据库层原子 UPDATE（setSql），避免读改写丢失更新。返回当前 like_count。
     */
    @Transactional
    public int toggleLike(Long commentId, Long userId) {
        CommentEntity comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        CommentLikeEntity existing = commentLikeMapper.selectOne(Wrappers.<CommentLikeEntity>lambdaQuery()
                .eq(CommentLikeEntity::getCommentId, commentId)
                .eq(CommentLikeEntity::getUserId, userId));
        if (existing != null) {
            // 已点赞 → 取消：逻辑删除点赞记录，计数原子 -1（不低于 0）
            commentLikeMapper.deleteById(existing.getId());
            commentMapper.update(null, Wrappers.<CommentEntity>lambdaUpdate()
                    .eq(CommentEntity::getId, commentId)
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
        } else {
            // 未点赞 → 点赞：插入点赞记录；命中唯一键冲突（并发 / 曾取消）则复活旧记录
            CommentLikeEntity like = new CommentLikeEntity();
            like.setCommentId(commentId);
            like.setUserId(userId);
            like.setIsDeleted(0);
            try {
                commentLikeMapper.insert(like);
            } catch (DuplicateKeyException ex) {
                commentLikeMapper.revive(commentId, userId);
            }
            commentMapper.update(null, Wrappers.<CommentEntity>lambdaUpdate()
                    .eq(CommentEntity::getId, commentId)
                    .setSql("like_count = like_count + 1"));
        }
        CommentEntity latest = commentMapper.selectById(commentId);
        return latest == null || latest.getLikeCount() == null ? 0 : latest.getLikeCount();
    }
}
