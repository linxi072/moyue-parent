package com.moyue.risk.behavior.model;

import lombok.Data;

import java.util.Map;

/**
 * 行为风控看板指标（P2-C）。
 * 由 {@code BehaviorRiskService.metrics()} 聚合：事件总量 / 决策总量 /
 * 各处置结论计数（BLOCK / REVIEW / PASS）/ 按规则编码分组计数。
 */
@Data
public class RiskMetricsVO {

    /** 累计行为事件数（risk_behavior_event） */
    private long totalEvents;

    /** 累计决策数（risk_decision） */
    private long totalDecisions;

    /** BLOCK 级决策数（已拦截） */
    private long blockCount;

    /** REVIEW 级决策数（转人工复核） */
    private long reviewCount;

    /** PASS 级决策数（放行） */
    private long passCount;

    /** 按规则编码分组决策数：ruleCode -> 计数 */
    private Map<String, Long> byRuleCode;
}
