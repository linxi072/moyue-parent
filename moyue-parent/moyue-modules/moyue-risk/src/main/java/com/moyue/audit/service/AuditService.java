package com.moyue.audit.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyue.api.content.client.ChapterClient;
import com.moyue.api.social.client.CommentClient;
import com.moyue.audit.entity.AuditTaskEntity;
import com.moyue.audit.mapper.AuditTaskMapper;
import com.moyue.common.exception.BizException;
import com.moyue.common.core.domain.R;
import com.moyue.common.core.domain.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 审核任务业务：查询待投递任务 + 审核裁决（通过 / 驳回）。
 * 裁决时经 Feign 回写目标业务状态（章节 status 2/3、评论 status 1/2），
 * 再把本地消息表置为「2 已完成」。
 *
 * <p>说明：audit_task 是本地消息表，理想形态由调度器异步投递；
 * 当前 Feign 回写与本地更新处于同一事务，属「尽力而为」的简化实现，
 * 跨服务最终一致性依赖后续引入消息队列（见架构文档路线图）。</p>
 *
 * <p>P2-15 由 moyue-platform 整包迁入 moyue-risk，包名零变更。</p>
 */
@Service
public class AuditService {

    /** 本地消息表状态：2 已完成 */
    private static final int STATUS_FINISHED = 2;

    private static final int BIZ_CHAPTER = 1;
    private static final int BIZ_COMMENT = 2;

    /** 章节审核通过 / 驳回后的目标状态（chapter.status） */
    private static final int CHAPTER_PUBLISHED = 2;
    private static final int CHAPTER_REJECTED = 3;

    /** 评论审核通过 / 驳回后的目标状态（comment.status） */
    private static final int COMMENT_APPROVED = 1;
    private static final int COMMENT_REJECTED = 2;

    @Autowired
    private AuditTaskMapper auditTaskMapper;

    @Autowired(required = false)
    private ChapterClient chapterClient;

    @Autowired(required = false)
    private CommentClient commentClient;

    /**
     * 查询 status=0 的待投递任务（本地消息表未消费记录）。
     * 16-21：支持按 bizType 过滤（1 章节 / 2 评论）；传 2 即「待审评论」口径。
     */
    public List<AuditTaskEntity> listPending(Integer bizType) {
        return listTasks(0, bizType);
    }

    /**
     * 查询审核任务，status / bizType 为空则不过滤（按创建时间升序）。
     */
    public List<AuditTaskEntity> listTasks(Integer status, Integer bizType) {
        return auditTaskMapper.selectList(Wrappers.<AuditTaskEntity>lambdaQuery()
                .eq(status != null, AuditTaskEntity::getStatus, status)
                .eq(bizType != null, AuditTaskEntity::getBizType, bizType)
                .orderByAsc(AuditTaskEntity::getCreateTime));
    }

    /**
     * 审核裁决：passed=true 通过 / false 驳回。
     * 先回写业务状态，再置本地任务为已完成；同一任务重复裁决被拒绝（幂等保护）。
     * 16-20：落库审核意见 remark 与操作人 operatorId（可空，取网关注入 X-User-Id）。
     */
    @Transactional
    public AuditTaskEntity decide(Long taskId, boolean passed, String remark, Long operatorId) {
        AuditTaskEntity task = auditTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (task.getStatus() != null && task.getStatus() == STATUS_FINISHED) {
            throw new BizException(ResultCode.PARAM_ERROR, "该审核任务已完成，请勿重复裁决");
        }
        applyBusinessStatus(task, passed);
        task.setStatus(STATUS_FINISHED);
        if (remark != null && !remark.isBlank()) {
            task.setRemark(remark.trim());
        }
        task.setOperatorId(operatorId);
        task.setUpdateTime(LocalDateTime.now());
        auditTaskMapper.updateById(task);
        return task;
    }

    /** 按业务类型回写目标业务状态 */
    private void applyBusinessStatus(AuditTaskEntity task, boolean passed) {
        Integer bizType = task.getBizType();
        String action = passed ? "通过" : "驳回";
        if (Objects.equals(bizType, BIZ_CHAPTER)) {
            if (chapterClient == null) {
                throw new BizException(ResultCode.INTERNAL_ERROR, "章节服务不可用，审核回写失败");
            }
            R<Void> resp = chapterClient.auditChapter(task.getBizId(),
                    passed ? CHAPTER_PUBLISHED : CHAPTER_REJECTED);
            ensureSuccess(resp, "章节" + action);
        } else if (Objects.equals(bizType, BIZ_COMMENT)) {
            if (commentClient == null) {
                throw new BizException(ResultCode.INTERNAL_ERROR, "评论服务不可用，审核回写失败");
            }
            R<Void> resp = commentClient.auditComment(task.getBizId(),
                    passed ? COMMENT_APPROVED : COMMENT_REJECTED);
            ensureSuccess(resp, "评论" + action);
        } else {
            throw new BizException(ResultCode.PARAM_ERROR, "未知业务类型：" + bizType);
        }
    }

    /**
     * 校验下游回写结果。
     * 关键：全局异常处理器把业务错误以 HTTP 200 + R.code != 0 返回，Feign 默认不抛异常，
     * 故必须显式判定 code，否则「回写失败」会被误判为成功、任务被错误置为已完成。
     */
    private void ensureSuccess(R<Void> resp, String action) {
        if (resp == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, action + "回写失败：下游无响应");
        }
        if (resp.getCode() != ResultCode.SUCCESS.getCode()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, action + "回写失败：" + resp.getMessage());
        }
    }
}
