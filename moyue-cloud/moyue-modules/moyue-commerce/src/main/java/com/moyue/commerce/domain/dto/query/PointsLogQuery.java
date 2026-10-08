package com.moyue.commerce.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 积分流水查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PointsLogQuery extends PageQuery {

    /** 用户 ID */
    private Long userId;

    /** 业务类型：1 充值 / 2 打赏 / 3 消费 / 4 退款 */
    private Integer bizType;
}
