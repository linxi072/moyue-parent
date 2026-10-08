package com.moyue.system.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperLogQuery extends PageQuery {

    /** 操作人账号 */
    private String operName;

    /** 模块标题 */
    private String title;

    /** 业务类型 */
    private Integer businessType;

    /** 状态：0 正常 / 1 异常 */
    private Integer status;

    /** 操作时间起（yyyy-MM-dd HH:mm:ss） */
    private String beginTime;

    /** 操作时间止 */
    private String endTime;
}
