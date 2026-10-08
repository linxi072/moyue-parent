package com.moyue.common.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：统一主键（雪花 ID）、审计字段、逻辑删除。
 *
 * <p>约定（架构说明书 6.5 / 11.1）：<ul>
 *   <li>主键统一雪花 ID（bigint），由 IdType.ASSIGN_ID 分配；</li>
 *   <li>时间字段统一 create_time / update_time（datetime），自动维护；</li>
 *   <li><b>所有表必须包含 is_deleted</b>，否则查询会命中 Unknown column。</li>
 * </ul>
 *
 * @author moyue
 */
@Data
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 备注 */
    private String remark;

    /** 逻辑删除：0 未删除 / 1 已删除 */
    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
