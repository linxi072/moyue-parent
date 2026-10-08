package com.moyue.system.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨服务用户契约（api-system）。
 *
 * <p><b>为什么不直接复用 moyue-system 的 UserVO</b>：api 模块被所有服务依赖，
 * 若 DTO 定义在业务模块里，就会形成「api ← 依赖 → 业务模块」的反向依赖。
 * 契约 DTO 必须住在 api 模块；字段名与 UserVO 对齐，Feign 反序列化时多余字段自动忽略。
 *
 * @author moyue
 */
@Data
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 主体性质：1 读者 / 2 作者 / 3 运营 */
    private Integer userType;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态：0 停用 / 1 正常 */
    private Integer status;

    /** 归属部门 ID */
    private Long deptId;

    /** 角色标识集合 */
    private List<String> roleKeys;

    /** 最近登录时间 */
    private LocalDateTime loginDate;
}
