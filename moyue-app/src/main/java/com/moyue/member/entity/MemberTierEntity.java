package com.moyue.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员等级（订阅套餐）配置实体，映射 member_tier 表。
 * 一条记录代表一种可订阅套餐（如基础包月 / 高级包月），承载价格、有效期与权益。
 */
@Data
@TableName("member_tier")
public class MemberTierEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 套餐编码（全局唯一，如 MONTHLY_BASIC / MONTHLY_PRO） */
    private String tierCode;

    /** 套餐名称 */
    private String tierName;

    /** 包月价格（元） */
    private BigDecimal monthlyPrice;

    /** 有效期天数（如 30 天） */
    private Integer durationDays;

    /** 免广告权益：0 否 / 1 是 */
    private Integer adFree;

    /** 折扣率（1.00 无折扣，0.90 表示 9 折） */
    private BigDecimal discountRate;

    /** 专属徽章名（可空） */
    private String badge;

    /** 展示排序 */
    private Integer sort;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
