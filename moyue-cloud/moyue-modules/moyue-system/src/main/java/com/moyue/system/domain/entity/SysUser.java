package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 统一用户表实体（读者 / 作者 / 运营）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录账号，运营必填唯一，C 端为 NULL */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** BCrypt 密码 */
    private String password;

    /** 所属部门 ID */
    private Long deptId;

    /** 主体性质：1 读者 / 2 作者 / 3 运营 */
    private Integer userType;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 头像 URL */
    private String avatar;

    /** 状态：0 禁用 / 1 正常 */
    private Integer status;

    /** 最近登录 IP */
    private String loginIp;

    /** 最近登录时间 */
    private java.time.LocalDateTime loginDate;
}
