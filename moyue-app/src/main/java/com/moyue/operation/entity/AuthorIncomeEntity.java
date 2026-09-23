package com.moyue.operation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作者稿酬流水实体，映射 author_income 表（V1 建表）。
 * 注意：author_income 表**无 is_deleted 列**，故本实体不含逻辑删除字段，
 * 全局 logic-delete-field: isDeleted 不会对本表生效（MyBatis-Plus 仅对存在该属性的实体追加条件）。
 */
@Data
@TableName("author_income")
public class AuthorIncomeEntity {

    /** 流水主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 作者 ID → user.id */
    private Long authorId;

    /** 作品 ID → book.id */
    private Long bookId;

    /** 来源订单号（打赏分成） */
    private String orderNo;

    /** 类型：1 订阅 / 2 打赏分成 / 3 全勤奖 / 4 买断分成（P0-1 新增，100% 入账） */
    private Integer incomeType;

    /** 金额（元） */
    private BigDecimal amount;

    /** 结算月份 YYYY-MM */
    private String settleMonth;

    /**
     * 关联结算单 ID（V16 新增）。
     * 非空表示该流水已被纳入某张 settlement_order，避免重复结算；查到为 null 的流水方可进入结算聚合。
     */
    private Long settlementId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
