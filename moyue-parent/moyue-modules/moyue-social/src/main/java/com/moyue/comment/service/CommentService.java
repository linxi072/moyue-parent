package com.moyue.comment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.commerce.PointsClient;
import com.moyue.api.social.dto.CommentDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.api.commerce.dto.PointsAwardDTO;
import com.moyue.comment.entity.CommentEntity;
import com.moyue.comment.entity.CommentLikeEntity;
import com.moyue.comment.mapper.CommentLikeMapper;
import com.moyue.comment.mapper.CommentMapper;
import com.moyue.common.exception.BizException;
import com.moyue.common.core.domain.R;
import com.moyue.common.core.domain.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 评论业务：按书籍分页查询评论、发表评论（默认待审）、删除（本人 / 管理员）、点赞切换。
 * 发表评论的 userId 一律由调用方从网关注入头取得，绝不信任请求体。
 * 发表成功后发评论奖励积分（P1-10 生产者侧，bizType=3）；奖励失败不阻断评论主流程。
 */
@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private static final int ROLE_ADMIN = 3;

    /** 评论奖励积分（P1-10） */
    private static final int COMMENT_AWARD_POINTS = 2;

    /** 评论奖励 bizType（与 points_flow.biz_type 注释对齐） */
    private static final int BIZ_COMMENT_AWARD = 3;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private CommentLikeMapper commentLikeMapper;

    /** 积分服务客户端；不可用时评论主流程不受影响（奖励降级跳过） */
    @Autowired(required = false)
    private PointsClient pointsClient;

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

    // ------------------------------ Entity -> DTO ------------------------------

    /**
     * 实体转 DTO：对外接口（含 Feign CommentClient）统一返回 DTO，与实体解耦。
     * 故意不映射 auditScore（机审风险分，属内部风控数据）与 isDeleted（内部标记），
     * 避免经 Feign / HTTP 暴露给调用方；DTO 未声明 chapterId，同样不映射。
     */
    public static CommentDTO toDto(CommentEntity e) {
        if (e == null) {
            return null;
        }
        CommentDTO d = new CommentDTO();
        d.setId(e.getId());
        d.setUserId(e.getUserId());
        d.setBookId(e.getBookId());
        d.setContent(e.getContent());
        d.setStatus(e.getStatus());
        d.setLikeCount(e.getLikeCount());
        d.setCreateTime(e.getCreateTime());
        return d;
    }

    /** 分页结果整体转 DTO */
    public static PageResult<CommentDTO> toDtoPage(PageResult<CommentEntity> page) {
        if (page == null) {
            return null;
        }
        PageResult<CommentDTO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPage(page.getPage());
        result.setSize(page.getSize());
        result.setRecords(page.getRecords() == null ? null
                : page.getRecords().stream().map(CommentService::toDto).toList());
        return result;
    }

    /** 发表评论：userId 取当前登录用户；状态默认 0 待审，点赞数默认 0；成功后发评论奖励（降级不阻断） */
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
        awardCommentPoints(userId, e.getId());
        return e;
    }

    /**
     * 评论奖励发放（P1-10 生产者侧，bizType=3）。
     * 积分服务不可用 / 发放失败仅记日志：评论已落库，奖励是旁路不能回滚主流程。
     */
    private void awardCommentPoints(Long userId, Long commentId) {
        if (pointsClient == null) {
            log.warn("[comment] 积分服务不可用，评论奖励跳过：userId={}, commentId={}", userId, commentId);
            return;
        }
        try {
            PointsAwardDTO award = new PointsAwardDTO();
            award.setUserId(userId);
            award.setBizType(BIZ_COMMENT_AWARD);
            award.setPoints(COMMENT_AWARD_POINTS);
            award.setRemark("评论奖励：评论 " + commentId);
            R<Integer> resp = pointsClient.award(award);
            // 关键：Feign 不抛业务异常（HTTP 200 + R.code != 0），必须显式校验 code
            if (resp == null || resp.getCode() != ResultCode.SUCCESS.getCode()) {
                log.warn("[comment] 评论奖励发放失败（已忽略）：userId={}, commentId={}, resp={}",
                        userId, commentId, resp);
            }
        } catch (Exception ex) {
            log.warn("[comment] 评论奖励调用异常（已忽略）：userId={}, commentId={}", userId, commentId, ex);
        }
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

    /**
     * 审核回写（内部端点专用，不经网关）：status 1=已通过 / 2=已驳回。
     * 由 moyue-audit 审核裁决后经 Feign 调用。
     */
    @Transactional
    public void auditComment(Long commentId, Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BizException(ResultCode.PARAM_ERROR, "审核状态非法（仅支持 1 已通过 / 2 已驳回）");
        }
        CommentEntity e = commentMapper.selectById(commentId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        e.setStatus(status);
        commentMapper.updateById(e);
    }
}
