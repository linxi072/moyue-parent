package com.moyue.risk.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 举报工单实体（风控域）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_report_ticket")
public class ReportTicket extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务类型：1 作品 / 2 评论 / 3 用户 / 4 帖子 */
    private Integer bizType;

    /** 被举报对象业务 ID */
    private String bizId;

    /** 举报人用户 ID */
    private Long reporterId;

    /** 举报原因 */
    private String reason;

    /** 被举报内容摘要（快照） */
    private String content;

    /** 状态：0 待处理 / 1 已处理 / 2 驳回 */
    private Integer status;

    /** 处理人 */
    private String handler;

    /** 处理说明 */
    private String handleReason;

    // 状态常量
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_HANDLED = 1;
    public static final int STATUS_REJECTED = 2;

    // 业务类型常量
    public static final int BIZ_BOOK = 1;
    public static final int BIZ_COMMENT = 2;
    public static final int BIZ_USER = 3;
    public static final int BIZ_POST = 4;
}
