package com.moyue.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作品完结申请实体，映射 book_finish_apply 表（V11）。
 * 作者提交申请 → 管理员裁决；通过时由服务层把 book.status 置 2（已完结）。
 */
@Data
@TableName("book_finish_apply")
public class BookFinishApplyEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 作品 ID → book.id */
    private Long bookId;

    /** 申请人（作者）ID → user.id */
    private Long authorId;

    /** 完结申请理由 */
    private String reason;

    /** 审核状态：0 待审核 / 1 通过 / 2 驳回 */
    private Integer status;

    /** 审核人（管理员）ID → user.id */
    private Long auditorId;

    /** 审核意见（通过 / 驳回理由） */
    private String auditRemark;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
