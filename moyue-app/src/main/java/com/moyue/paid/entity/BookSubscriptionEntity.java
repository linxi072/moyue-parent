package com.moyue.paid.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 整本订阅记录，映射 book_subscription 表。
 * 状态机：0 待支付 / 1 生效中 / 2 已过期 / 3 已取消；生效中且在 [start_time, end_time] 窗口内解锁该书全部付费章节。
 */
@Data
@TableName("book_subscription")
public class BookSubscriptionEntity {

    /** 订阅主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 → user.id */
    private Long userId;

    /** 作品 → book.id */
    private Long bookId;

    /** 订单号 */
    private String orderNo;

    /** 订阅金额（元） */
    private BigDecimal amount;

    /** 支付渠道 stub/real */
    private String channel;

    /** 状态：0 待支付 / 1 生效中 / 2 已过期 / 3 已取消 */
    private Integer status;

    /** 生效开始 */
    private LocalDateTime startTime;

    /** 生效结束（NULL 表示永久） */
    private LocalDateTime endTime;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
