package com.moyue.ai.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 任务查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiTaskQuery extends PageQuery {

    /** 任务类型：1 续写 / 2 润色 / 3 摘要 / 4 大纲 */
    private Integer taskType;

    /** 状态：0 待处理 / 1 成功 / 2 失败 */
    private Integer status;

    /** 发起用户 */
    private Long userId;

    /** 提示词，模糊匹配 */
    private String prompt;
}
