package com.moyue.operation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 稿酬结算单实体，映射 settlement_order 表（V16 建表）。
 * 状态机：0 待结算 / 1 已结算待打款 / 2 已打款 / 3 打款失败。
 * is_deleted 列沿用本模块既有实体（SysUser/SysMenu 等）的 {@code @TableLogic} 约定做逻辑删除。
 */
@Data
@TableName("settlement_order")
public class SettlementOrderEntity {

    /** 结算单主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 作者 ID → user.id */
    private Long authorId;

    /** 结算月份 YYYY-MM */
    private String period;

    /** 结算总额（元） */
    private BigDecimal totalAmount;

    /** 状态：0 待结算 / 1 已结算待打款 / 2 已打款 / 3 打款失败 */
    private Integer status;

    /** 打款渠道 1 微信 / 2 支付宝 */
    private Integer payChannel;

    /** 渠道流水号（幂等键） */
    private String paySerial;

    /** 审核 / 打款备注 */
    private String remark;

    /** 审核 / 打款操作人 */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    @TableField("is_deleted")
    private Integer isDeleted;
}
