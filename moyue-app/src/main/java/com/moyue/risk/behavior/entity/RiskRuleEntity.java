package com.moyue.risk.behavior.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风控规则配置实体，映射 risk_rule 表。
 * 规则「配置化非硬编码」：类型 / 阈值 / 处置动作均存库，支持热刷与开关。
 */
@Data
@TableName("risk_rule")
public class RiskRuleEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 规则编码（全局唯一，如 R_FREQ_LOGIN） */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型：FREQ(频控) / DEVICE_MULTI_ACCOUNT(同设备多账号) / POINTS_ANOMALY(积分异常) / ACCOUNT_THEFT(盗号) */
    private String ruleType;

    /** 是否启用：0 关 / 1 开 */
    private Integer enabled;

    /** 命中处置动作：LIMIT(限流) / MARK(标记) / REVIEW(转人工复核) / BLOCK(拦截) */
    private String action;

    /** 规则参数 JSON（windowMinutes / threshold 等，按类型解释） */
    private String configJson;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
