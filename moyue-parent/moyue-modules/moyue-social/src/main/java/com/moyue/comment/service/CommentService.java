package com.moyue.comment.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.commerce.client.PointsClient;
import com.moyue.api.risk.client.RiskClient;
import com.moyue.api.risk.dto.ModerationRequestDTO;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.api.social.dto.CommentDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.api.commerce.dto.PointsAwardDTO;
import com.moyue.comment.entity.CommentEntity;
import com.moyue.comment.entity.CommentLikeEntity;
import com.moyue.comment.mapper.CommentLikeMapper;
import com.moyue.comment.mapper.CommentMapper;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
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
 *
 * <p>P2-15 机审 hook：评论发表落库前经 {@link RiskClient} 送 moyue-risk 机审——
 * REJECT 抛 CONTENT_BLOCKED(20002) 阻断落库；REVIEW 照常落库保持待审（status=0，
 * audit_task 由 moyue-risk 写入转人工）；机审客户端不可用时安全降级不阻断评论。</p>
 */
@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private static final int ROLE_ADMIN = 3;

    /** 评论奖励积分（P1-10） */
    private static final int COMMENT_AWARD_POINTS = 2;

    /** 评论奖励 bizType（与 points_flow.biz_type 注释对齐） */
    private static final int BIZ_COMMENT_AWARD = 3;

    /** 机审结论：命中拦截级敏感词（RiskClient 契约） */
    private static final String DECISION_REJECT = "REJECT";

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private CommentLikeMapper commentLikeMapper;

    /** 积分服务客户端；不可用时评论主流程不受影响（奖励降级跳过） */
    @Autowired(required = false)
    private PointsClient pointsClient;

    /** 内容安全服务客户端（P2-15 机审 hook）；risk 未注册时安全降级 */
    @Autowired(required = false)
    private RiskClient riskClient;

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
        // P2-15 机审：预生成雪花 ID 并在落库前送审（REJECT 抛 20002 阻断，不产生脏数据；
        // REVIEW 保持待审 status=0，audit_task 已由 moyue-risk 写入转人工）
        e.setId(IdWorker.getId());
        moderateOrThrow(e.getId(), content);
        commentMapper.insert(e);
        awardCommentPoints(userId, e.getId());
        return e;
    }

    /**
     * P2-15 机审 hook（落库前）：送 moyue-risk 机审。
     * REJECT → 抛 CONTENT_BLOCKED(20002)；PASS / REVIEW → 放行（评论本就默认待审）；
     * 机审客户端未注册 / 熔断降级（fallback 返回 R.code=40002）/ 调用异常 → 不阻断业务（安全降级）。
     */
    private void moderateOrThrow(Long commentId, String content) {
        if (riskClient == null) {
            return;
        }
        try {
            ModerationRequestDTO req = new ModerationRequestDTO();
            req.setBizType(2);
            req.setBizId(commentId);
            req.setContent(content);
            R<ModerationResultDTO> resp = riskClient.moderate(req);
            // fallback 降级 / 业务失败：视为机审不可用，放行并告警
            if (resp == null || resp.getCode() != ResultCode.SUCCESS.getCode() || resp.getData() == null) {
                log.warn("[comment] 机审客户端降级，跳过机审：commentId={}, code={}",
                        commentId, resp == null ? "无响应" : resp.getCode());
                return;
            }
            if (DECISION_REJECT.equals(resp.getData().getDecision())) {
                throw new BizException(ResultCode.CONTENT_BLOCKED);
            }
        } catch (BizException ex) {
            // 机审拦截属业务结论，原样上抛
            throw ex;
        } catch (Exception ex) {
            log.warn("[comment] 机审调用异常，跳过机审：commentId={}, err={}", commentId, ex.getMessage());
        }
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
    /** 按 ID 查询评论实体（供内部端点 / 归属人解析；不存在返回 null，由调用方降级） */
    public CommentEntity getById(Long commentId) {
        if (commentId == null) {
            return null;
        }
        return commentMapper.selectById(commentId);
    }

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
