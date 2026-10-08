package com.moyue.system.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图（不返回 password）。
 *
 * @author moyue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private Long deptId;
    /** 部门名称，联查填充 */
    private String deptName;
    private Integer userType;
    private String phone;
    private String email;
    private String avatar;
    private Integer status;
    private String loginIp;
    private LocalDateTime loginDate;
    private LocalDateTime createTime;
    private String remark;
    /** 已分配角色 ID */
    private List<Long> roleIds;
}
