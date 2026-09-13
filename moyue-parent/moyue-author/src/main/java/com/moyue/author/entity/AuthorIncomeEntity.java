package com.moyue.author.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作者稿酬流水实体，映射 author_income 表。
 * 说明：author_income 表无 is_deleted 字段，故不引入逻辑删除字段。
 */
@Data
@TableName("author_income")
public class AuthorIncomeEntity {

    /** 流水主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 作者 ID → user.id */
    private Long authorId;

    /** 作品 ID（订阅/打赏分成来源，可为空） */
    private Long bookId;

    /** 来源订单号（打赏分成，可为空） */
    private String orderNo;

    /** 类型：1 订阅 / 2 打赏分成 / 3 全勤奖 */
    private Integer incomeType;

    /** 金额（元） */
    private BigDecimal amount;

    /** 结算月份 YYYY-MM */
    private String settleMonth;

    /** 创建时间 */
    private LocalDateTime createTime;
}
