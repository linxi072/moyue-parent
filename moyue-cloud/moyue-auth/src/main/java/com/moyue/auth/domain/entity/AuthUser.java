package com.moyue.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录校验用的用户视图（映射到 sys_user）。
 *
 * <p><b>刻意不复用 moyue-system 的 SysUser</b>：auth 若依赖 system 模块，就变成
 * 「登录要先等 system 可用」，违背 ADR-3 的独立性。这里是精简的只读映射，
 * 只保留认证链路需要的字段；字段变更时两处需同步，已在 README 登记为已知代价。
 *
 * <p>注意：本实体不继承 BaseEntity —— auth 不写业务表（登录日志除外），
 * 无需审计字段填充。
 *
 * @author moyue
 */
@Data
@TableName("sys_user")
public class AuthUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录账号 */
    private String username;

    /** BCrypt 密码 */
    private String password;

    /** 主体性质：1 读者 / 2 作者 / 3 运营 */
    private Integer userType;

    /** 昵称 */
    private String nickname;

    /** 手机号（C 端登录账号） */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 头像 */
    private String avatar;

    /** 状态：0 正常 / 1 停用 */
    private Integer status;

    /** 归属部门 */
    private Long deptId;

    /** 最近登录 IP */
    private String loginIp;

    /** 最近登录时间 */
    private LocalDateTime loginDate;

    /** 逻辑删除 */
    private Integer isDeleted;
}
