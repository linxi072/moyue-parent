package com.moyue.risk.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 举报工单查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportTicketQuery extends PageQuery {

    /** 业务类型：1 作品 / 2 评论 / 3 用户 / 4 帖子 */
    private Integer bizType;

    /** 状态：0 待处理 / 1 已处理 / 2 驳回 */
    private Integer status;

    /** 被举报对象业务 ID */
    private String bizId;

    /** 举报人用户 ID */
    private Long reporterId;
}
