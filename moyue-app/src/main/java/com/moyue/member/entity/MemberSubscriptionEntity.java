package com.moyue.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员订阅记录实体，映射 member_subscription 表。
 * 一条记录代表某用户一次订阅生命周期（待支付 → 生效中 → 已过期 / 已取消）。
 */
@Data
@TableName("member_subscription")
public class MemberSubscriptionEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订阅用户 ID → user.id */
    private Long userId;

    /** 套餐编码 → member_tier.tier_code */
    private String tierCode;

    /** 反规范化快照：套餐名称 */
    private String tierName;

    /** 订阅状态：0 待支付 / 1 生效中 / 2 已过期 / 3 已取消 */
    private Integer status;

    /** 生效开始时间 */
    private LocalDateTime startTime;

    /** 生效结束时间（到期依据） */
    private LocalDateTime endTime;

    /** 订阅订单号 */
    private String orderNo;

    /** 支付渠道流水号 */
    private String paySerial;

    /** 支付渠道标识 */
    private String channel;

    /** 逻辑删除：0 否 / 1 是（全局未启用 logic-delete，须显式标注防物理删） */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    // -----------------------------------------------------------------
    // P2-B 连续订阅（自动续费）字段
    // -----------------------------------------------------------------

    /** 自动续费开关：0 否 / 1 是 */
    private Boolean autoRenew;

    /** 续费周期快照（天）：subscribe 时取 tier.durationDays，整期续费按此延长，避免 tier 编辑致周期漂移 */
    private Integer renewCycleDays;

    /** 下次续费计划时刻 = endTime − 窗口（窗口 = max(1, renewCycleDays/3) 天）；扫描起点 */
    private LocalDateTime renewAt;

    /** 最近一次续费触发时刻 */
    private LocalDateTime lastRenewAt;

    /** 续费连续失败次数 */
    private Integer renewFailCount;

    /** 最近续费结果：0 成功 / 1 失败 / NULL 未续费 */
    private Integer renewLastStatus;

    /** 最近续费结果摘要（失败原因） */
    private String renewLastMsg;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
