package com.moyue.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体，映射 sys_user 表（运营后台操作员账号）。
 * 注意：password 为 BCrypt 哈希，对外响应一律经 {@code UserVO} 屏蔽，避免泄露。
 */
@Data
@TableName("sys_user")
public class SysUserEntity {

    /** 用户主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 部门 ID → sys_dept.id，0 未分配 */
    private Long deptId;

    /** 登录账号 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** BCrypt 密码哈希 */
    private String password;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

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
