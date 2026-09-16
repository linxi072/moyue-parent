package com.moyue.risk.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.content.client.BookClient;
import com.moyue.api.content.client.ChapterClient;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.content.dto.ChapterDTO;
import com.moyue.api.message.client.MessageDispatchClient;
import com.moyue.api.message.dto.MessageDispatchDTO;
import com.moyue.api.social.client.CommentClient;
import com.moyue.api.social.dto.CommentDTO;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 举报闭环业务（P2-15 R-3）：读者提交 → 管理员处理 → 自动隐藏 + 结果触达。
 *
 * <p>处理规则：</p>
 * <ul>
 *   <li>属实且对象为章节（targetType=2）→ 经 {@link ChapterClient} 下架（chapter.status=3）；</li>
 *   <li>属实且对象为评论（targetType=3）→ 经 {@link CommentClient} 驳回（comment.status=2）；</li>
 *   <li>属实且对象为书籍 / 用户（targetType=1/4）→ 仅记录处理意见，
 *       由管理员在书籍域 / 用户域二次操作（扩展点，避免跨服务直写他域表）。</li>
 * </ul>
 * <p>处理完成后按 REPORT_RESULT 模板（V13 种子）经 {@link MessageDispatchClient}
 * 给举报人发站内信；触达失败只告警，不回滚处理结果。</p>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** 举报对象类型（V13 report.target_type） */
    public static final int TARGET_BOOK = 1;
    public static final int TARGET_CHAPTER = 2;
    public static final int TARGET_COMMENT = 3;
    public static final int TARGET_USER = 4;

    /** 举报状态：0 待处理 / 1 属实 / 2 驳回 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_CONFIRMED = 1;
    private static final int STATUS_REJECTED = 2;

    /** 章节驳回状态（chapter.status，与审核回写语义一致） */
    private static final int CHAPTER_REJECTED = 3;
    /** 评论驳回状态（comment.status，与审核回写语义一致） */
    private static final int COMMENT_REJECTED = 2;

    /** 举报原因类型上限（1 违规内容 / 2 广告 / 3 侵权 / 4 其他） */
    private static final int MAX_REASON_TYPE = 4;

    /** 逻辑删除：已删除 */
    private static final int DELETED = 1;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired(required = false)
    private ChapterClient chapterClient;

    @Autowired(required = false)
    private CommentClient commentClient;

    /** 书籍服务客户端：举报书籍 / 章节时经 book 解析作者（被处理方）；不可用时 owner 通知降级跳过 */
    @Autowired(required = false)
    private BookClient bookClient;

    /** 站内信触达客户端（moyue-message）；不可用时结果触达降级跳过 */
    @Autowired(required = false)
    private MessageDispatchClient messageDispatchClient;

    // ------------------------------ 读者端 ------------------------------

    /**
     * 提交举报（需登录，reporterId 取网关注入 X-User-Id）。
     * 同一举报人对同一对象存在「待处理」举报时不再重复落库（幂等防刷）。
     */
    @Transactional
    public ReportEntity submit(long reporterId, Integer targetType, Long targetId,
                               Integer reasonType, String reason) {
        if (targetType == null || targetType < TARGET_BOOK || targetType > TARGET_USER) {
            throw new BizException(ResultCode.PARAM_ERROR, "举报对象类型非法（1 书籍 / 2 章节 / 3 评论 / 4 用户）");
        }
        if (targetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "举报对象 ID 不能为空");
        }
        int rt = reasonType == null ? 1 : reasonType;
        if (rt < 1 || rt > MAX_REASON_TYPE) {
            throw new BizException(ResultCode.PARAM_ERROR, "举报原因类型非法");
        }
        Long dup = reportMapper.selectCount(Wrappers.<ReportEntity>lambdaQuery()
                .eq(ReportEntity::getReporterId, reporterId)
                .eq(ReportEntity::getTargetType, targetType)
                .eq(ReportEntity::getTargetId, targetId)
                .eq(ReportEntity::getStatus, STATUS_PENDING)
                .eq(ReportEntity::getIsDeleted, 0));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "该对象已有待处理的举报，请耐心等待");
        }
        ReportEntity e = new ReportEntity();
        e.setReporterId(reporterId);
        e.setTargetType(targetType);
        e.setTargetId(targetId);
        e.setReasonType(rt);
        e.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        e.setStatus(STATUS_PENDING);
        e.setIsDeleted(0);
        reportMapper.insert(e);
        return e;
    }

    /** 我的举报（分页，按创建时间倒序） */
    public PageResult<ReportEntity> listMine(long reporterId, int page, int size) {
        Page<ReportEntity> p = new Page<>(page, size);
        reportMapper.selectPage(p, Wrappers.<ReportEntity>lambdaQuery()
                .eq(ReportEntity::getReporterId, reporterId)
                .eq(ReportEntity::getIsDeleted, 0)
                .orderByDesc(ReportEntity::getCreateTime));
        return toPageResult(p, page, size);
    }

    // ------------------------------ 管理端 ------------------------------

    /** 举报列表（分页，status / targetType 可选过滤） */
    public PageResult<ReportEntity> adminPage(Integer status, Integer targetType, int page, int size) {
        Page<ReportEntity> p = new Page<>(page, size);
        reportMapper.selectPage(p, Wrappers.<ReportEntity>lambdaQuery()
                .eq(ReportEntity::getIsDeleted, 0)
                .eq(status != null, ReportEntity::getStatus, status)
                .eq(targetType != null, ReportEntity::getTargetType, targetType)
                .orderByDesc(ReportEntity::getCreateTime));
        return toPageResult(p, page, size);
    }

    /**
     * 处理举报：passed=true 属实 / false 驳回。
     * 属实且对象为章节 / 评论时自动隐藏（经已有 Feign 契约回写目标状态）；
     * 处理结果按 REPORT_RESULT 模板触达举报人（失败不回滚）。
     * 幂等：已处理的举报不允许重复裁决。
     */
    @Transactional
    public ReportEntity handle(Long id, boolean passed, String remark, Long operatorId) {
        ReportEntity report = reportMapper.selectOne(Wrappers.<ReportEntity>lambdaQuery()
                .eq(ReportEntity::getId, id)
                .eq(ReportEntity::getIsDeleted, 0));
        if (report == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "举报不存在");
        }
        if (report.getStatus() != null && report.getStatus() != STATUS_PENDING) {
            throw new BizException(ResultCode.PARAM_ERROR, "该举报已处理，请勿重复操作");
        }
        if (passed) {
            // 属实：章节 / 评论自动隐藏（复用既有审核回写契约）；书籍 / 用户留扩展点
            if (Objects.equals(report.getTargetType(), TARGET_CHAPTER)) {
                ensureSuccess(callAuditChapter(report.getTargetId()), "章节下架");
            } else if (Objects.equals(report.getTargetType(), TARGET_COMMENT)) {
                ensureSuccess(callAuditComment(report.getTargetId()), "评论隐藏");
            } else {
                // 扩展点：书籍 / 用户举报属实暂不自动处置，由管理员在对应域手动处理
                // （书籍下架 → moyue-content 书籍管理接口；用户封禁 → moyue-account 用户管理接口）
                log.info("[report] 举报属实（对象为书籍/用户，需人工二次处置）：id={}, targetType={}, targetId={}",
                        id, report.getTargetType(), report.getTargetId());
            }
        }
        report.setStatus(passed ? STATUS_CONFIRMED : STATUS_REJECTED);
        report.setHandlerId(operatorId);
        report.setHandleRemark(remark == null || remark.isBlank() ? null : remark.trim());
        report.setHandleTime(LocalDateTime.now());
        reportMapper.updateById(report);

        // 结果触达：REPORT_RESULT 模板（V13 种子），失败只告警不回滚处理结果
        notifyReporter(report, passed);
        // 被处理方通知：OWNER_NOTICE 模板（V17 种子），通知内容归属人，失败只告警不回滚
        notifyOwner(report, passed);
        return report;
    }

    // ------------------------------ 内部 ------------------------------

    /** 章节下架回写（Feign 不可用 / 调用异常返回 null，由 ensureSuccess 统一转业务异常） */
    private R<Void> callAuditChapter(Long chapterId) {
        if (chapterClient == null) {
            return null;
        }
        try {
            return chapterClient.auditChapter(chapterId, CHAPTER_REJECTED);
        } catch (Exception ex) {
            log.warn("[report] 章节下架回写异常：chapterId={}, err={}", chapterId, ex.getMessage());
            return null;
        }
    }

    /** 评论隐藏回写（Feign 不可用 / 调用异常返回 null，由 ensureSuccess 统一转业务异常） */
    private R<Void> callAuditComment(Long commentId) {
        if (commentClient == null) {
            return null;
        }
        try {
            return commentClient.auditComment(commentId, COMMENT_REJECTED);
        } catch (Exception ex) {
            log.warn("[report] 评论隐藏回写异常：commentId={}, err={}", commentId, ex.getMessage());
            return null;
        }
    }

    /**
     * 校验下游回写结果。
     * 关键：全局异常处理器以 HTTP 200 + R.code != 0 承载业务错误，Feign 默认不抛异常，
     * 必须显式判定 code，否则「下架失败」会被误判为成功。
     */
    private void ensureSuccess(R<Void> resp, String action) {
        if (resp == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, action + "失败：内容服务不可用");
        }
        if (resp.getCode() != ResultCode.SUCCESS.getCode()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, action + "失败：" + resp.getMessage());
        }
    }

    /** 按 REPORT_RESULT 模板给举报人发站内信（触达失败不阻断处理流程） */
    private void notifyReporter(ReportEntity report, boolean passed) {
        if (messageDispatchClient == null) {
            log.warn("[report] 触达服务不可用，举报结果通知跳过：reportId={}", report.getId());
            return;
        }
        try {
            MessageDispatchDTO dto = new MessageDispatchDTO();
            dto.setUserId(report.getReporterId());
            dto.setTemplateCode("REPORT_RESULT");
            dto.setParams(Map.of(
                    "targetDesc", describeTarget(report),
                    "result", passed ? "属实" : "驳回"));
            dto.setBizType("REPORT");
            dto.setBizId(report.getId());
            R<?> resp = messageDispatchClient.dispatch(dto);
            // Feign 不抛业务异常（HTTP 200 + R.code），须显式判码；失败仅告警
            if (resp == null || resp.getCode() != ResultCode.SUCCESS.getCode()) {
                log.warn("[report] 举报结果触达失败（已忽略）：reportId={}, resp={}", report.getId(), resp);
            }
        } catch (Exception ex) {
            log.warn("[report] 举报结果触达异常（已忽略）：reportId={}, err={}", report.getId(), ex.getMessage());
        }
    }

    /** 通知被处理内容归属人（章节作者 / 评论者 / 书籍作者 / 被举报用户），走站内信 INBOX；失败只告警不回滚 */
    private void notifyOwner(ReportEntity report, boolean passed) {
        if (messageDispatchClient == null) {
            log.warn("[report] 触达服务不可用，owner 通知跳过：reportId={}", report.getId());
            return;
        }
        Long ownerId = resolveOwnerId(report);
        if (ownerId == null) {
            log.warn("[report] 无法解析被处理方用户，owner 通知跳过：reportId={}, targetType={}, targetId={}",
                    report.getId(), report.getTargetType(), report.getTargetId());
            return;
        }
        try {
            MessageDispatchDTO dto = new MessageDispatchDTO();
            dto.setUserId(ownerId);
            dto.setTemplateCode("OWNER_NOTICE");
            dto.setParams(Map.of(
                    "targetDesc", describeTarget(report),
                    "result", passed ? "属实，已下架/隐藏" : "驳回（举报不成立）",
                    "reason", (report.getHandleRemark() != null && !report.getHandleRemark().isBlank())
                            ? report.getHandleRemark().trim() : "经平台审核不属实"));
            // 强制站内信渠道（INBOX=1），保证处置闭环在站内信真实可达
            dto.setChannels(List.of(1));
            dto.setBizType("REPORT");
            dto.setBizId(report.getId());
            R<?> resp = messageDispatchClient.dispatch(dto);
            if (resp == null || resp.getCode() != ResultCode.SUCCESS.getCode()) {
                log.warn("[report] owner 通知触达失败（已忽略）：reportId={}, ownerId={}, resp={}",
                        report.getId(), ownerId, resp);
            }
        } catch (Exception ex) {
            log.warn("[report] owner 通知异常（已忽略）：reportId={}, ownerId={}, err={}",
                    report.getId(), ownerId, ex.getMessage());
        }
    }

    /** 按举报对象类型解析被处理方用户 ID：书籍/章节→作者，评论→评论者，用户→其自身 */
    private Long resolveOwnerId(ReportEntity report) {
        Integer targetType = report.getTargetType();
        Long targetId = report.getTargetId();
        if (targetId == null) {
            return null;
        }
        try {
            if (Objects.equals(targetType, TARGET_BOOK)) {
                if (bookClient == null) {
                    return null;
                }
                R<BookSummaryDTO> bookResp = bookClient.getBook(targetId);
                return (bookResp == null || bookResp.getData() == null) ? null : bookResp.getData().getAuthorId();
            } else if (Objects.equals(targetType, TARGET_CHAPTER)) {
                if (chapterClient == null || bookClient == null) {
                    return null;
                }
                R<ChapterDTO> chapterResp = chapterClient.getChapter(targetId);
                if (chapterResp == null || chapterResp.getData() == null
                        || chapterResp.getData().getBookId() == null) {
                    return null;
                }
                R<BookSummaryDTO> bookResp = bookClient.getBook(chapterResp.getData().getBookId());
                return (bookResp == null || bookResp.getData() == null) ? null : bookResp.getData().getAuthorId();
            } else if (Objects.equals(targetType, TARGET_COMMENT)) {
                if (commentClient == null) {
                    return null;
                }
                R<CommentDTO> commentResp = commentClient.getComment(targetId);
                return (commentResp == null || commentResp.getData() == null) ? null : commentResp.getData().getUserId();
            } else if (Objects.equals(targetType, TARGET_USER)) {
                return targetId;
            }
        } catch (Exception ex) {
            log.warn("[report] 解析被处理方异常（已忽略）：targetType={}, targetId={}, err={}",
                    targetType, targetId, ex.getMessage());
        }
        return null;
    }

    /** 举报对象描述（模板占位符 {targetDesc}） */
    private String describeTarget(ReportEntity report) {
        return switch (report.getTargetType() == null ? 0 : report.getTargetType()) {
            case TARGET_BOOK -> "书籍#" + report.getTargetId();
            case TARGET_CHAPTER -> "章节#" + report.getTargetId();
            case TARGET_COMMENT -> "评论#" + report.getTargetId();
            case TARGET_USER -> "用户#" + report.getTargetId();
            default -> "对象#" + report.getTargetId();
        };
    }

    private PageResult<ReportEntity> toPageResult(IPage<ReportEntity> result, int page, int size) {
        PageResult<ReportEntity> pr = new PageResult<>();
        pr.setTotal(result.getTotal());
        pr.setPage(page);
        pr.setSize(size);
        pr.setRecords(result.getRecords());
        return pr;
    }
}
