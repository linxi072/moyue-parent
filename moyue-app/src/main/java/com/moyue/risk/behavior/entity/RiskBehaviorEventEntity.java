package com.moyue.risk.behavior.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户行为事件实体，映射 risk_behavior_event 表。
 * 行为风控的采集源（登录 / 签到 / 兑换 / 打赏 / 发布等），仅追加写入、不可变，
 * 故不做逻辑删除（与 audit_task 同为不可变审计日志，不承载软删语义）。
 */
@Data
@TableName("risk_behavior_event")
public class RiskBehaviorEventEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 行为用户 ID → user.id */
    private Long userId;

    /** 设备指纹 / 设备 ID（登录与设备维度风控用） */
    private String deviceId;

    /** 事件类型：LOGIN / SIGN_IN / REDEEM / REWARD / PUBLISH */
    private String eventType;

    /** 关联业务主键（如兑换订单 ID） */
    private Long bizId;

    /** 来源 IP */
    private String ip;

    /** 风险分（预留，当前默认 0） */
    private Integer riskScore;

    /** 采集时间 */
    private LocalDateTime createTime;
}
