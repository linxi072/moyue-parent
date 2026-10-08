package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色与部门关联表（数据权限，data_scope = 2 时生效）。
 *
 * @author moyue
 */
@Data
@TableName("sys_role_dept")
public class SysRoleDept implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long roleId;
    private Long deptId;
}
