package com.moyue.commerce.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 积分账户查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PointsAccountQuery extends PageQuery {

    /** 用户 ID */
    private Long userId;
}
