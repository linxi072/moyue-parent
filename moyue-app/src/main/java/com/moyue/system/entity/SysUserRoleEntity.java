package com.moyue.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户-角色关联实体，映射 sys_user_role 表（联合主键，无单独 ID）。
 * 仅用于 insert / 按条件 delete，不对外暴露。
 */
@Data
@TableName("sys_user_role")
public class SysUserRoleEntity implements Serializable {

    /** 用户 ID → sys_user.id */
    private Long userId;

    /** 角色 ID → sys_role.id */
    private Long roleId;
}
