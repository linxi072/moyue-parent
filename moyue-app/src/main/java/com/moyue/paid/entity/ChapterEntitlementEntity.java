package com.moyue.paid.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 章节解锁（单章购买）记录，映射 chapter_entitlement 表。
 * 购买即标记已解锁（status=1），expire_time 为 NULL 表示永久解锁。
 */
@Data
@TableName("chapter_entitlement")
public class ChapterEntitlementEntity {

    /** 解锁记录主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 → user.id */
    private Long userId;

    /** 章节 → chapter.id */
    private Long chapterId;

    /** 作品 → book.id */
    private Long bookId;

    /** 订单号 */
    private String orderNo;

    /** 实付金额（元，已含会员折扣） */
    private BigDecimal amount;

    /** 支付渠道 stub/real */
    private String channel;

    /** 状态：0 待支付 / 1 已解锁 / 2 已退款 */
    private Integer status;

    /** 解锁有效期（NULL 表示永久） */
    private LocalDateTime expireTime;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
