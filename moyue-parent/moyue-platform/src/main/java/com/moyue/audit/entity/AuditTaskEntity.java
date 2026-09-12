package com.moyue.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核任务实体，映射 audit_task 表（本地消息表，保证审核消息不丢）。
 * 说明：audit_task 表无 is_deleted 字段，故不引入逻辑删除字段。
 */
@Data
@TableName("audit_task")
public class AuditTaskEntity {

    /** 任务主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务类型：1 章节 / 2 评论 */
    private Integer bizType;

    /** 业务主键 */
    private Long bizId;

    /** 状态：0 待投递 / 1 已投递 / 2 已完成 / 3 死信 */
    private Integer status;

    /** 重试次数 */
    private Integer retryCount;

    /** 审核意见（16-20：通过 / 驳回理由，可空；V10 迁移新增） */
    private String remark;

    /** 审核操作人用户 ID（16-20：网关注入 X-User-Id，可空；V10 迁移新增） */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
