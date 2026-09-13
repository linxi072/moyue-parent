package com.moyue.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统部门实体，映射 sys_dept 表。
 * 逻辑删除经 {@code @TableLogic} 在实体级启用（全局 logic-delete 配置注释未开，按 application.yml 建议逐实体开启）。
 */
@Data
@TableName("sys_dept")
public class SysDeptEntity {

    /** 部门主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父部门 ID，0 表示顶级 */
    private Long parentId;

    /** 部门名称 */
    private String deptName;

    /** 显示顺序 */
    private Integer orderNum;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态：0 禁用 / 1 正常 */
    private Integer status;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    @TableField("is_deleted")
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 子部门（非表字段，树形构建用） */
    @TableField(exist = false)
    private List<SysDeptEntity> children;
}
