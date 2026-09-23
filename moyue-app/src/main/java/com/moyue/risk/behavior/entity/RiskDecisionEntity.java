package com.moyue.risk.behavior.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风控决策 / 处置记录实体，映射 risk_decision 表。
 * 命中规则后落盘的处置结论（PASS / REVIEW / BLOCK），仅追加写入、不可变审计日志，
 * 故不做逻辑删除（与 risk_behavior_event / audit_task 一致）。
 */
@Data
@TableName("risk_decision")
public class RiskDecisionEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 命中用户 ID */
    private Long userId;

    /** 命中设备 ID */
    private String deviceId;

    /** 命中规则编码 */
    private String ruleCode;

    /** 触发事件类型 */
    private String eventType;

    /** 处置结论：PASS / REVIEW / BLOCK */
    private String decision;

    /** 风险等级：0 无 / 1 低 / 2 中 / 3 高 */
    private Integer riskLevel;

    /** 说明 */
    private String message;

    /** 决策时间 */
    private LocalDateTime createTime;
}
