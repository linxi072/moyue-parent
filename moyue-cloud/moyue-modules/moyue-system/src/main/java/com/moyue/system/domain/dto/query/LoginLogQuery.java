package com.moyue.system.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录日志查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginLogQuery extends PageQuery {

    /** 登录账号 */
    private String username;

    /** 登录 IP */
    private String ip;

    /** 状态：0 成功 / 1 失败 */
    private Integer status;

    /** 登录时间起 */
    private String beginTime;

    /** 登录时间止 */
    private String endTime;
}
