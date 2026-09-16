package com.moyue.risk;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 内容安全统计看板响应（P1-4）：聚合敏感词命中、审核任务与举报三类指标。
 * 全部为只读聚合，无写操作；数据来源复用 sensitive_word / audit_task / report 三张表。
 */
@Data
public class RiskDashboardVO {

    /** 敏感词总数（未删除） */
    private long sensitiveWordTotal;

    /** 敏感词命中次数累计（sum(hit_count)） */
    private long sensitiveWordHitTotal;

    /** 命中 TOP（倒序，取前 10） */
    private List<TopHitWord> topHitWords;

    /** 审核任务按 status 计数：0 待投递 / 1 已投递 / 2 已完成 / 3 死信 */
    private Map<Integer, Long> auditTaskByStatus;

    /** 审核任务总数 */
    private long auditTaskTotal;

    /** 举报按 status 计数：0 待处理 / 1 属实 / 2 驳回（仅未删除） */
    private Map<Integer, Long> reportByStatus;

    /** 举报总数（未删除） */
    private long reportTotal;

    /** 命中 TOP 词（看板展示用，仅透出安全字段） */
    @Data
    public static class TopHitWord {
        private Long id;
        private String word;
        private Integer level;
        private Integer hitCount;
    }
}
