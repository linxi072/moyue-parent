package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色与菜单关联表（无逻辑删除列，物理删除）。
 *
 * @author moyue
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long roleId;
    private Long menuId;
}
