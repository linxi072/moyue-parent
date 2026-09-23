package com.moyue.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，映射 user 表。
 * 注意：不含 password 字段，避免泄露密码哈希。
 */
@Data
@TableName("user")
public class UserEntity {

    /** 用户主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 手机号，登录账号 */
    private String phone;

    /** 用户昵称 */
    private String nickname;

    /** 头像地址 */
    private String avatarUrl;

    /** 邮箱（触达渠道：邮件；可空，未绑定则不投邮件） */
    private String email;

    /** 设备推送令牌（触达渠道：推送；可空，未绑定则不投推送） */
    private String deviceToken;

    /** 角色：1 读者 / 2 作者 / 3 管理员 */
    private Integer role;

    /** 状态：0 禁用 / 1 正常 */
    private Integer status;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
