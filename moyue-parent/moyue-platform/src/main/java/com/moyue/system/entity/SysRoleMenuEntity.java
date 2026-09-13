package com.moyue.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色-菜单关联实体，映射 sys_role_menu 表（联合主键，无单独 ID）。
 * 仅用于 insert / 按条件 delete，不对外暴露。
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenuEntity implements Serializable {

    /** 角色 ID → sys_role.id */
    private Long roleId;

    /** 菜单 ID → sys_menu.id */
    private Long menuId;
}
