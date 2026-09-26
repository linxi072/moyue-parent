package com.moyue.book.review.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.risk.client.RiskClient;
import com.moyue.api.risk.dto.ModerationRequestDTO;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.book.event.BookRatingChangedEvent;
import com.moyue.book.review.dto.ReviewDTO;
import com.moyue.book.review.entity.ReviewEntity;
import com.moyue.book.review.entity.ReviewLikeEntity;
import com.moyue.book.review.mapper.ReviewLikeMapper;
import com.moyue.book.review.mapper.ReviewMapper;
import com.moyue.book.review.mapper.ReviewSummary;
import com.moyue.book.service.BookService;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 书评与评分业务：提交书评（默认待审）、按作品分页列表（仅 status=1）、点赞切换、删除、审核回写、
 * 评分聚合回写 book、发布评分变更事件。整体镜像 comment 模块范式。
 *
 * <p>与 comment 列表查询的关键差异：本模块列表显式过滤 {@code status=1}（已通过），
 * 修正 comment 模块不过滤 status 导致待审/驳回评论也被曝光的缺陷。</p>
 *
 * <p>机审 hook（P2-15 范式）：书评落库前经 {@link RiskClient} 送 moyue-risk 机审——
 * REJECT 抛 CONTENT_BLOCKED(20002) 阻断落库；REVIEW 照常落库保持待审；
 * 机审客户端不可用时安全降级不阻断主流程。bizType 取本地常量 {@code BIZ_REVIEW=5}。</p>
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** 管理员角色标识 */
    private static final int ROLE_ADMIN = 3;

    /** 机审结论：命中拦截级敏感词（RiskClient 契约） */
    private static final String DECISION_REJECT = "REJECT";

    /** 书评机审 bizType（本地常量，避免改动 AuditService 的全局常量） */
    private static final int BIZ_REVIEW = 5;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private ReviewLikeMapper reviewLikeMapper;

    /** 书籍服务：回写评分聚合、刷新搜索索引；方法由并发 worker 在 BookService 上新增 */
    @Autowired
    private BookService bookService;

    /** 内容安全服务客户端（P2-15 机审 hook）；risk 未注册时安全降级 */
    @Autowired(required = false)
    private RiskClient riskClient;

    /** 领域事件发布器：评分变更后发 BookRatingChangedEvent */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** 按 book_id 分页查询书评，仅返回 status=1（已通过），按创建时间倒序 */
    public PageResult<ReviewEntity> listByBook(Long bookId, int page, int size) {
        Page<ReviewEntity> p = new Page<>(page, size);
        reviewMapper.selectPage(p, Wrappers.<ReviewEntity>lambdaQuery()
                .eq(ReviewEntity::getBookId, bookId)
                .eq(ReviewEntity::getStatus, 1)
                .orderByDesc(ReviewEntity::getCreateTime));

        PageResult<ReviewEntity> r = new PageResult<>();
        r.setTotal(p.getTotal());
        r.setPage((int) p.getCurrent());
        r.setSize((int) p.getSize());
        r.setRecords(p.getRecords());
        return r;
    }

    /**
     * 提交书评：userId 取当前登录用户；status 默认 0 待审，点赞数默认 0。
     * 落库前经机审 hook（REJECT 抛 CONTENT_BLOCKED 阻断）；落库后重算评分聚合并发布事件。
     */
    public ReviewEntity addReview(Long userId, Long bookId, Integer score, String content) {
        if (bookId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "作品 ID 不能为空");
        }
        if (score == null || score < 1 || score > 5) {
            throw new BizException(ResultCode.PARAM_ERROR, "星级评分需在 1~5 之间");
        }
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "书评内容不能为空");
        }
        ReviewEntity e = new ReviewEntity();
        e.setUserId(userId);
        e.setBookId(bookId);
        e.setScore(score);
        e.setContent(content);
        e.setStatus(0);
        e.setLikeCount(0);
        e.setIsDeleted(0);
        // 预生成雪花 ID，供机审 bizId 关联（与 comment 模块一致）
        e.setId(IdWorker.getId());
        moderateOrThrow(e.getId(), content);
        reviewMapper.insert(e);
        recomputeAndPublish(bookId);
        return e;
    }

    /**
     * 机审 hook（落库前）：送 moyue-risk 机审。
     * REJECT → 抛 CONTENT_BLOCKED(20002)；PASS / REVIEW → 放行（书评本就默认待审）；
     * 机审客户端未注册 / 熔断降级 / 调用异常 → 不阻断业务（安全降级）。
     */
    private void moderateOrThrow(Long reviewId, String content) {
        if (riskClient == null) {
            return;
        }
        try {
            ModerationRequestDTO req = new ModerationRequestDTO();
            req.setBizType(BIZ_REVIEW);
            req.setBizId(reviewId);
            req.setContent(content);
            R<ModerationResultDTO> resp = riskClient.moderate(req);
            // fallback 降级 / 业务失败：视为机审不可用，放行并告警
            if (resp == null || resp.getCode() != ResultCode.SUCCESS.getCode() || resp.getData() == null) {
                log.warn("[review] 机审客户端降级，跳过机审：reviewId={}, code={}",
                        reviewId, resp == null ? "无响应" : resp.getCode());
                return;
            }
            if (DECISION_REJECT.equals(resp.getData().getDecision())) {
                throw new BizException(ResultCode.CONTENT_BLOCKED);
            }
        } catch (BizException ex) {
            // 机审拦截属业务结论，原样上抛
            throw ex;
        } catch (Exception ex) {
            log.warn("[review] 机审调用异常，跳过机审：reviewId={}, err={}", reviewId, ex.getMessage());
        }
    }

    /** 删除书评（逻辑删除）：仅书评人本人或管理员；随后重算评分聚合 */
    @Transactional
    public void deleteReview(Long reviewId, long userId, int role) {
        ReviewEntity e = reviewMapper.selectById(reviewId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (role != ROLE_ADMIN && !Objects.equals(e.getUserId(), userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        // 逻辑删除：必须走 deleteById（MyBatis-Plus @TableLogic 会将其翻译为 UPDATE is_deleted=1）。
        // 不可使用 updateById(e.setIsDeleted(1))——updateById 会忽略 @TableLogic 列，导致软删除未持久化、
        // 书评仍计入评分聚合与列表（与 comment 模块 deleteById 范式一致）。
        reviewMapper.deleteById(e.getId());
        recomputeAndPublish(e.getBookId());
    }

    /**
     * 点赞切换：已点赞则取消（逻辑删除点赞记录 + like_count-1），否则点赞（防重复 + like_count+1）。
     * 计数用数据库层原子 UPDATE（setSql），避免读改写丢失更新。返回当前 like_count。
     */
    @Transactional
    public int toggleLike(Long reviewId, Long userId) {
        ReviewEntity review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        ReviewLikeEntity existing = reviewLikeMapper.selectOne(Wrappers.<ReviewLikeEntity>lambdaQuery()
                .eq(ReviewLikeEntity::getReviewId, reviewId)
                .eq(ReviewLikeEntity::getUserId, userId));
        if (existing != null) {
            // 已点赞 → 取消：逻辑删除点赞记录，计数原子 -1（不低于 0）
            reviewLikeMapper.deleteById(existing.getId());
            reviewMapper.update(null, Wrappers.<ReviewEntity>lambdaUpdate()
                    .eq(ReviewEntity::getId, reviewId)
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
        } else {
            // 未点赞 → 点赞：插入点赞记录；命中唯一键冲突（并发 / 曾取消）则复活旧记录
            ReviewLikeEntity like = new ReviewLikeEntity();
            like.setReviewId(reviewId);
            like.setUserId(userId);
            like.setIsDeleted(0);
            try {
                reviewLikeMapper.insert(like);
            } catch (DuplicateKeyException ex) {
                reviewLikeMapper.revive(reviewId, userId);
            }
            reviewMapper.update(null, Wrappers.<ReviewEntity>lambdaUpdate()
                    .eq(ReviewEntity::getId, reviewId)
                    .setSql("like_count = like_count + 1"));
        }
        ReviewEntity latest = reviewMapper.selectById(reviewId);
        return latest == null || latest.getLikeCount() == null ? 0 : latest.getLikeCount();
    }

    /**
     * 审核回写（内部端点专用，不经网关）：status 1=已通过 / 2=已驳回。
     * 由 moyue-audit 审核裁决后经 Feign 调用；审核状态变化影响评分聚合，需重算并发布事件。
     */
    @Transactional
    public void auditReview(Long reviewId, Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BizException(ResultCode.PARAM_ERROR, "审核状态非法（仅支持 1 已通过 / 2 已驳回）");
        }
        ReviewEntity e = reviewMapper.selectById(reviewId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        e.setStatus(status);
        reviewMapper.updateById(e);
        recomputeAndPublish(e.getBookId());
    }

    /** 按 ID 查询书评实体（供内部端点 / 归属人解析；不存在返回 null，由调用方降级） */
    public ReviewEntity getById(Long id) {
        if (id == null) {
            return null;
        }
        return reviewMapper.selectById(id);
    }

    /**
     * 重算评分聚合并回写 book，随后发布评分变更事件。
     * 仅聚合 status=1（已通过）的书评，保证对外评分口径与列表一致。
     */
    private void recomputeAndPublish(Long bookId) {
        ReviewSummary s = reviewMapper.selectSummary(bookId);
        bookService.updateRatingSummary(bookId, s.getAvg(),
                s.getCnt() == null ? 0 : s.getCnt().intValue());
        eventPublisher.publishEvent(new BookRatingChangedEvent(this, bookId));
    }
}
