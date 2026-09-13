package com.moyue.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知实体，映射 notice 表。
 * notice 表由公共模块 V3 迁移创建，专供消息服务使用。
 *
 * <p>P2-14 由 moyue-social 整包迁入 moyue-message，包名零变更。</p>
 */
@Data
@TableName("notice")
public class NoticeEntity {

    /** 主键 */
    private Long id;

    /** 接收用户 ID */
    private Long userId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型：1 系统 / 2 互动 / 3 公告 */
    private Integer type;

    /** 是否已读：0 未读 / 1 已读 */
    private Integer isRead;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 逻辑删除标记：0 未删除 / 1 已删除（对应全局逻辑删除字段 isDeleted） */
    private Integer isDeleted;
}
