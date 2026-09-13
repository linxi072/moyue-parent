package com.moyue.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色实体，映射 sys_role 表。
 * data_scope 表达数据权限：1 全部 / 2 自定义部门 / 3 仅本人；data_scope=2 时 dept_ids 为逗号分隔的部门 ID。
 */
@Data
@TableName("sys_role")
public class SysRoleEntity {

    /** 角色主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色名称 */
    private String roleName;

    /** 角色标识（权限校验用，如 admin） */
    private String roleKey;

    /** 数据权限：1 全部 / 2 自定义部门 / 3 仅本人 */
    private Integer dataScope;

    /** 自定义数据权限部门 ID，逗号分隔（data_scope=2） */
    private String deptIds;

    /** 状态：0 禁用 / 1 正常 */
    private Integer status;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    @TableField("is_deleted")
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
