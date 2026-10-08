package com.moyue.message.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内信查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageQuery extends PageQuery {

    /** 接收人 */
    private Long toUser;

    /** 类型：1 系统 / 2 活动 / 3 私信 */
    private Integer type;

    /** 已读标记：0 未读 / 1 已读 */
    private Integer readFlag;

    /** 标题，模糊匹配 */
    private String title;
}
