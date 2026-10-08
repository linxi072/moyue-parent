package com.moyue.system.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门视图，支持树形嵌套。
 *
 * @author moyue
 */
@Data
public class DeptVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String ancestors;
    private String deptName;
    private Integer orderNum;
    private String leader;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime createTime;

    /** 子部门 */
    private List<DeptVO> children = new ArrayList<>();
}
