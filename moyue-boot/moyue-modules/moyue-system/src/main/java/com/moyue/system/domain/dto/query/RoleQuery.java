package com.moyue.system.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleQuery extends PageQuery {

    /** 角色名称，模糊匹配 */
    private String roleName;

    /** 角色标识，模糊匹配 */
    private String roleKey;

    /** 状态：0 停用 / 1 正常 */
    private Integer status;
}
