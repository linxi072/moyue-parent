package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;


/**
 * 在线用户审计实体（Redis 主存 + 本表审计）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_online")
public class SysUserOnline extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 令牌 ID，唯一索引 */
    private String tokenId;

    /** 用户 ID */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 主体类型：1 读者 / 2 作者 / 3 运营 */
    private Integer userType;

    /** 部门 ID，C 端为空 */
    private Long deptId;

    /** 登录 IP */
    private String ip;

    /** IP 归属地 */
    private String location;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 设备类型：PC / H5 / APP / 小程序 */
    private String device;

    /** 登录时间 */
    private java.time.LocalDateTime loginTime;

    /** 最后访问时间 */
    private java.time.LocalDateTime lastAccessTime;

    /** 令牌过期时间 */
    private java.time.LocalDateTime expireTime;

    /** 会话状态：0 已强退 / 1 在线 */
    private Integer status;
}
