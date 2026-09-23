package com.moyue.risk.moderation;

import com.moyue.api.risk.dto.ModerationRequestDTO;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.audit.entity.AuditTaskEntity;
import com.moyue.audit.mapper.AuditTaskMapper;
import com.moyue.common.ResultCode;
import com.moyue.risk.sensitive.SensitiveWordEngine;
import com.moyue.risk.sensitive.SensitiveWordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 机审业务（P2-15 R-1/R-2）：实现 RiskClient 契约 POST /api/v1/internal/risk/moderate。
 *
 * <p>判定规则（架构设计 §七-5，与 ModerationResultDTO 注释一致）：</p>
 * <ul>
 *   <li>无命中 → PASS；</li>
 *   <li>命中 level=2 且无 level=1 → REVIEW：内容照常落库（由调用方标记审核中），
 *       本服务写 audit_task（biz_type：1 章节 / 2 评论 / 3 书籍 / 4 用户，对齐 V13 注释）转人工；</li>
 *   <li>出现 level=1 命中 → REJECT（调用方抛业务异常阻断落库）。</li>
 * </ul>
 * <p>说明：V13 未建 moderation_log 表，按设计不新增表，机审结果只落 audit_task（REVIEW 时）
 * 与敏感词命中统计（sensitive_word.hit_count，V15 新增列）。</p>
 */
@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    /** 处置结论 */
    public static final String DECISION_PASS = "PASS";
    public static final String DECISION_REVIEW = "REVIEW";
    public static final String DECISION_REJECT = "REJECT";

    /** audit_task 状态：0 待投递（待人工审核） */
    private static final int TASK_STATUS_PENDING = 0;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Autowired
    private AuditTaskMapper auditTaskMapper;

    /**
     * 执行机审：标题 + 正文进敏感词引擎，按命中最高等级产出处置结论。
     *
     * @param request 机审请求（bizType 1 章节 / 2 评论 / 3 书籍）
     * @return 处置结果（decision / hitWords / maxLevel / taskId）
     */
    public ModerationResultDTO moderate(ModerationRequestDTO request) {
        ModerationResultDTO result = new ModerationResultDTO();
        result.setDecision(DECISION_PASS);
        result.setHitWords(new ArrayList<>());
        result.setMaxLevel(0);

        String text = buildText(request);
        SensitiveWordEngine engine = sensitiveWordService.engine();
        List<SensitiveWordEngine.Hit> hits = engine.match(text);
        if (hits.isEmpty()) {
            return result;
        }

        List<String> words = new ArrayList<>(hits.size());
        int maxLevel = 0;
        for (SensitiveWordEngine.Hit hit : hits) {
            words.add(hit.getWord());
            maxLevel = Math.max(maxLevel, hit.getLevel());
        }
        result.setHitWords(words);
        result.setMaxLevel(maxLevel);

        // 命中统计（旁路，失败不阻断机审结论）
        sensitiveWordService.increaseHitCount(words);

        // 等级语义：1=拦截、2=告警（数值更大反而更宽松），不能按 maxLevel 比大小判定。
        // 只要出现任一 level=1 命中即 REJECT（混合命中从严）；仅 level=2 时走 REVIEW。
        boolean blocked = hits.stream().anyMatch(h -> h.getLevel() == SensitiveWordEngine.LEVEL_BLOCK);
        if (blocked) {
            // 命中拦截级：REJECT，不写审核任务（内容不会落库）
            result.setDecision(DECISION_REJECT);
            return result;
        }

        // 命中告警级：REVIEW + 写 audit_task 转人工（bizId 可空：章节创建场景先审后落库）
        result.setDecision(DECISION_REVIEW);
        Long taskId = createAuditTask(request, words);
        result.setTaskId(taskId);
        return result;
    }

    /** 拼接待审文本（标题行 + 正文） */
    private String buildText(ModerationRequestDTO request) {
        if (request == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            sb.append(request.getTitle().trim()).append('\n');
        }
        if (request.getContent() != null) {
            sb.append(request.getContent());
        }
        return sb.toString();
    }

    /**
     * REVIEW 判定写入 audit_task（本地消息表，人工审核队列）。
     * 写入失败只告警并返回 null：机审结论照常返回 REVIEW，
     * 调用方内容已标记审核中，人工兜底可在后台补录任务（audit_task 写入方单一属主约束不破坏）。
     */
    private Long createAuditTask(ModerationRequestDTO request, List<String> words) {
        try {
            AuditTaskEntity task = new AuditTaskEntity();
            task.setBizType(request.getBizType());
            task.setBizId(request.getBizId());
            task.setStatus(TASK_STATUS_PENDING);
            task.setRetryCount(0);
            task.setRemark("机审命中告警词：" + String.join("、", words));
            task.setCreateTime(LocalDateTime.now());
            auditTaskMapper.insert(task);
            return task.getId();
        } catch (Exception ex) {
            log.warn("[moderation] audit_task 写入失败（机审结论不受影响）：bizType={}, err={}",
                    request == null ? null : request.getBizType(), ex.getMessage());
            return null;
        }
    }

    /** 供降级判断复用的成功码语义（保持与 common ResultCode 单一来源） */
    public static boolean isSuccess(int code) {
        return code == ResultCode.SUCCESS.getCode();
    }
}
