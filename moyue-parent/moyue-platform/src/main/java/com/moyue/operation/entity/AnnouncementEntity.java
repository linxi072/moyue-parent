package com.moyue.operation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运营公告实体，映射 announcement 表（V6 建表）。
 * 表含 is_deleted 字段，全局逻辑删除配置（logic-delete-field: isDeleted）自动生效。
 */
@Data
@TableName("announcement")
public class AnnouncementEntity {

    /** 公告主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 类型：1 站内公告 / 2 活动 / 3 系统维护 */
    private Integer type;

    /** 状态：0 草稿 / 1 已发布 / 2 已下线 */
    private Integer status;

    /** 是否置顶：0 否 / 1 是（显式声明列名，避免 is 前缀属性命名歧义） */
    @TableField("is_top")
    private Integer isTop;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
