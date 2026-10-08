package com.moyue.system.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {

    /** 部门 ID */
    private Long deptId;

    /** 关键字：账号 / 昵称 / 手机号 */
    private String keyword;

    /** 主体类型：1 读者 / 2 作者 / 3 运营 */
    private Integer userType;

    /** 状态：0 禁用 / 1 正常 */
    private Integer status;

    /** 创建时间起 */
    private String beginTime;

    /** 创建时间止 */
    private String endTime;
}
