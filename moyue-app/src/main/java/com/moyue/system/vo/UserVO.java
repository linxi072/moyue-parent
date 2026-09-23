package com.moyue.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统用户对外视图（屏蔽 password 哈希），列表 / 详情响应使用。
 */
@Data
public class UserVO {

    /** 用户主键 */
    private Long id;

    /** 部门 ID */
    private Long deptId;

    /** 部门名称（冗余展示，由服务层填充） */
    private String deptName;

    /** 登录账号 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 状态：0 禁用 / 1 正常 */
    private Integer status;

    /** 角色 ID 列表（由 sys_user_role 关联得出） */
    private List<Long> roleIds;

    /** 创建时间 */
    private LocalDateTime createTime;
}
