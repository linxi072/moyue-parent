package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 菜单权限表实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 菜单名称 */
    private String menuName;

    /** 父菜单 ID，顶级为 0 */
    private Long parentId;

    /** 显示顺序 */
    private Integer orderNum;

    /** 路由地址 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 路由参数 */
    private String query;

    /** 是否外链：0 是 / 1 否 */
    private Integer isFrame;

    /** 是否缓存：0 缓存 / 1 不缓存 */
    private Integer isCache;

    /** 菜单类型：M 目录 / C 菜单 / F 按钮 */
    private String menuType;

    /** 显示状态：0 隐藏 / 1 显示 */
    private Integer visible;

    /** 状态：0 停用 / 1 正常 */
    private Integer status;

    /** 权限标识 */
    private String perms;

    /** 菜单图标 */
    private String icon;
}
