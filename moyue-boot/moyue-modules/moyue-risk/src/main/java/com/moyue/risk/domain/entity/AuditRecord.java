package com.moyue.risk.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 审核工单实体（风控域）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_audit_record")
public class AuditRecord extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务类型：1 作品 / 2 评论 / 3 封面 */
    private Integer bizType;

    /** 业务 ID（作品 / 评论等主键） */
    private String bizId;

    /** 待审内容摘要 */
    private String content;

    /** 状态：0 待审 / 1 通过 / 2 驳回 */
    private Integer status;

    /** 审核人 */
    private String auditor;

    /** 驳回原因 */
    private String reason;
}
