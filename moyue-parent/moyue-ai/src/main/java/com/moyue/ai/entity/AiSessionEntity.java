package com.moyue.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 客服会话实体，映射 ai_session 表。
 */
@Data
@TableName("ai_session")
public class AiSessionEntity {

    /** 会话主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID → user.id */
    private Long userId;

    /** 会话标题（默认取首条提问截断） */
    private String title;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
