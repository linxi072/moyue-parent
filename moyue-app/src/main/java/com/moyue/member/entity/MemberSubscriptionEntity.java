package com.moyue.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
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

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
