package com.moyue.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.social.domain.dto.query.CommentQuery;
import com.moyue.social.domain.entity.Comment;
import com.moyue.social.domain.entity.CommentLike;
import com.moyue.social.domain.vo.CommentVO;
import com.moyue.social.mapper.CommentLikeMapper;
import com.moyue.social.mapper.CommentMapper;
import com.moyue.social.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评论实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;

    @Override
    public PageResult<CommentVO> pageComments(CommentQuery query) {
        var page = PageUtils.<Comment>page(query);
        var result = commentMapper.selectPage(page, new LambdaQueryWrapper<Comment>()
                .eq(query.getBookId() != null, Comment::getBookId, query.getBookId())
                .eq(query.getChapterId() != null, Comment::getChapterId, query.getChapterId())
                .eq(query.getUserId() != null, Comment::getUserId, query.getUserId())
                .orderByDesc(Comment::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createComment(Long userId, Long bookId, Long chapterId, Long replyTo, String content) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评论内容不能为空");
        }
        // book_id 在库中为 NOT NULL 且无默认值：缺失时必须走参数校验，
        // 否则会穿透到 MySQL 抛 DataIntegrityViolationException，前端只能收到 500。
        if (bookId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评论必须关联作品（bookId）");
        }
        Comment c = new Comment();
        c.setUserId(userId);
        c.setBookId(bookId);
        c.setChapterId(chapterId);
        c.setReplyTo(replyTo);
        c.setContent(content);
        c.setLikeCount(0);
        commentMapper.insert(c);
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, Long commentId) {
        Comment c = commentMapper.selectById(commentId);
        if (c == null) {
            throw BusinessException.notFound("评论");
        }
        if (!userId.equals(c.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅本人可删除评论");
        }
        commentMapper.deleteById(commentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int toggleLike(Long userId, Long commentId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (commentMapper.selectById(commentId) == null) {
            throw BusinessException.notFound("评论");
        }
        int liked = commentMapper.countLike(commentId, userId);
        if (liked == 0) {
            // 未赞 → 有历史行（含已逻辑删除）则复位置 0，否则插新行
            // （唯一键 uk_comment_like 不含 is_deleted，直插已删过的行会撞键）
            if (commentLikeMapper.countAny(commentId, userId) > 0) {
                commentMapper.reviveLike(commentId, userId);
            } else {
                CommentLike cl = new CommentLike();
                cl.setCommentId(commentId);
                cl.setUserId(userId);
                commentLikeMapper.insert(cl);
            }
            commentMapper.incLike(commentId);
        } else {
            // 已赞 → 撤销：逻辑删除点赞记录 + 计数 -1
            commentMapper.discardLike(commentId, userId);
            commentMapper.decLike(commentId);
        }
        Comment c = commentMapper.selectById(commentId);
        return c == null ? 0 : (c.getLikeCount() == null ? 0 : c.getLikeCount());
    }

    private CommentVO toVO(Comment c) {
        return CommentVO.builder()
                .id(c.getId()).bookId(c.getBookId()).chapterId(c.getChapterId())
                .userId(c.getUserId()).replyTo(c.getReplyTo()).content(c.getContent())
                .likeCount(c.getLikeCount() == null ? 0 : c.getLikeCount())
                .createTime(c.getCreateTime())
                .build();
    }

    // ============ 运营端（管理后台） ============

    @Override
    public PageResult<CommentVO> pageAdminComments(CommentQuery query) {
        var page = PageUtils.<Comment>page(query);
        var result = commentMapper.selectPage(page, new LambdaQueryWrapper<Comment>()
                .eq(query.getBookId() != null, Comment::getBookId, query.getBookId())
                .eq(query.getChapterId() != null, Comment::getChapterId, query.getChapterId())
                .eq(query.getUserId() != null, Comment::getUserId, query.getUserId())
                .eq(query.getStatus() != null, Comment::getStatus, query.getStatus())
                .eq(query.getTop() != null, Comment::getTop, query.getTop())
                .orderByDesc(Comment::getTop)
                .orderByDesc(Comment::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adminDelete(Long commentId) {
        Comment c = commentMapper.selectById(commentId);
        if (c == null) {
            throw BusinessException.notFound("评论");
        }
        // 逻辑删除（复用 BaseEntity @TableLogic，与 C 端删除口径一致）
        return commentMapper.deleteById(commentId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean top(Long commentId, int top) {
        Comment c = commentMapper.selectById(commentId);
        if (c == null) {
            throw BusinessException.notFound("评论");
        }
        if (top != 0 && top != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "置顶值不合法（0 或 1）");
        }
        Comment upd = new Comment();
        upd.setId(commentId);
        upd.setTop(top);
        return commentMapper.updateById(upd) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean audit(Long commentId, Integer status) {
        Comment c = commentMapper.selectById(commentId);
        if (c == null) {
            throw BusinessException.notFound("评论");
        }
        if (status == null || status < 0 || status > 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审核状态不合法（0 正常 / 1 待审核 / 2 已下架）");
        }
        Comment upd = new Comment();
        upd.setId(commentId);
        upd.setStatus(status);
        return commentMapper.updateById(upd) > 0;
    }
}
