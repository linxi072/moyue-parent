package com.moyue.system.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色视图。
 *
 * @author moyue
 */
@Data
@Builder
public class RoleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String roleName;
    private String roleKey;
    private Integer roleSort;
    private Integer dataScope;
    private Integer status;
    private LocalDateTime createTime;
    private String remark;
}
