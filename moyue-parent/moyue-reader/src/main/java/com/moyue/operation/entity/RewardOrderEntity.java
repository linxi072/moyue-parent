package com.moyue.operation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打赏订单实体，映射 reward_order 表。
 */
@Data
@TableName("reward_order")
public class RewardOrderEntity {

    /** 主键（全局 assign_id 策略生成） */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 用户 ID */
    private Long userId;

    /** 书籍 ID */
    private Long bookId;

    /** 章节 ID */
    private Long chapterId;

    /** 打赏金额 */
    private BigDecimal amount;

    /** 支付渠道：1 微信 / 2 支付宝（列类型 TINYINT，此处须为数值型，避免字符串隐式转换） */
    private Integer payChannel;

    /** 订单状态：0 待支付 / 1 已支付 / 2 已关闭 */
    private Integer status;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 逻辑删除标记：0 未删除 / 1 已删除（对应全局逻辑删除字段 isDeleted） */
    private Integer isDeleted;

    /** 书籍标题（16-23 出参 enrichment，经 Feign 查询填充；非表列） */
    @TableField(exist = false)
    private String bookTitle;

    /** 章节标题（16-23 出参 enrichment，经 Feign 查询填充；非表列） */
    @TableField(exist = false)
    private String chapterTitle;
}
