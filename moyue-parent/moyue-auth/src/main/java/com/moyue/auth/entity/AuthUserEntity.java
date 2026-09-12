package com.moyue.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证用户实体，映射 user 表（与 moyue-user 的 UserEntity 同表不同包，避免跨模块耦合）。
 */
@Data
@TableName("user")
public class AuthUserEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 手机号，登录账号 */
    private String phone;

    /** 用户昵称 */
    private String nickname;

    /** BCrypt 密码哈希 */
    private String password;

    /** 头像地址 */
    private String avatarUrl;

    /** 角色：1 读者 / 2 作者 / 3 管理员 */
    private Integer role;

    /** 状态：0 禁用 / 1 正常 */
    private Integer status;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
