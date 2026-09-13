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
 * 系统菜单实体，映射 sys_menu 表（目录 / 菜单 / 按钮三类）。
 * menu_type：0 目录 / 1 菜单 / 2 按钮；按钮级用 perms 标识（如 system:user:add）。
 */
@Data
@TableName("sys_menu")
public class SysMenuEntity {

    /** 菜单主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父菜单 ID，0 表示顶级 */
    private Long parentId;

    /** 菜单名称 */
    private String menuName;

    /** 类型：0 目录 / 1 菜单 / 2 按钮 */
    private Integer menuType;

    /** 路由地址 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 权限标识（按钮级） */
    private String perms;

    /** 图标 */
    private String icon;

    /** 显示顺序 */
    private Integer orderNum;

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

    /** 子菜单（非表字段，树形构建用） */
    @TableField(exist = false)
    private List<SysMenuEntity> children;
}
