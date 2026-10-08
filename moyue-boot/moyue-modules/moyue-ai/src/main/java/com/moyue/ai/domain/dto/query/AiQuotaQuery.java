package com.moyue.ai.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 配额查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiQuotaQuery extends PageQuery {

    /** 用户 ID */
    private Long userId;
}
