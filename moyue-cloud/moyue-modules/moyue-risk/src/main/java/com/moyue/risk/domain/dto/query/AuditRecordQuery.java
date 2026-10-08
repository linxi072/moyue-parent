package com.moyue.risk.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审核工单查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditRecordQuery extends PageQuery {

    /** 业务类型：1 作品 / 2 评论 / 3 封面 */
    private Integer bizType;

    /** 状态：0 待审 / 1 通过 / 2 驳回 */
    private Integer status;

    /** 业务 ID */
    private String bizId;

    /** 内容摘要，模糊匹配 */
    private String content;
}
