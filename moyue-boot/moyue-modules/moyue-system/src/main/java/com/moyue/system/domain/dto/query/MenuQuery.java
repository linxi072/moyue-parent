package com.moyue.system.domain.dto.query;

import lombok.Data;

import java.io.Serializable;

/**
 * 菜单查询条件（菜单为树形全量返回，不分页）。
 *
 * @author moyue
 */
@Data
public class MenuQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单名称，模糊匹配 */
    private String menuName;

    /** 状态：0 停用 / 1 正常 */
    private Integer status;
}
