package com.moyue.message.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageTemplateQuery extends PageQuery {

    /** 模板编码 / 标题，模糊匹配 */
    private String keyword;

    /** 类型：1 系统 / 2 活动 / 3 私信 */
    private Integer type;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;
}
