package com.moyue.risk.report;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报实体，映射 report 表（V13）。
 * <p>注意：全局逻辑删除未启用，is_deleted 由业务代码自行维护，查询需显式过滤 {@code is_deleted = 0}。</p>
 * <p>target_type 语义（V13 注释）：1 书籍 / 2 章节 / 3 评论 / 4 用户；
 * status 语义：0 待处理 / 1 属实 / 2 驳回。</p>
 */
@Data
@TableName("report")
public class ReportEntity {

    /** 举报主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 举报人 → user.id */
    private Long reporterId;

    /** 对象：1 书籍 / 2 章节 / 3 评论 / 4 用户 */
    private Integer targetType;

    /** 对象主键 */
    private Long targetId;

    /** 原因：1 违规内容 / 2 广告 / 3 侵权 / 4 其他 */
    private Integer reasonType;

    /** 补充说明 */
    private String reason;

    /** 状态：0 待处理 / 1 属实 / 2 驳回 */
    private Integer status;

    /** 处理人 → 管理员 ID */
    private Long handlerId;

    /** 处理意见 */
    private String handleRemark;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
