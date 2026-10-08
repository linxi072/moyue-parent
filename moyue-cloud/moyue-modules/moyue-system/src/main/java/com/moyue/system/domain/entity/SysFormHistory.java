package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 表单 Schema 历史版本实体（缺口 G-8 补充表）。
 *
 * <p>每次发布（status → 1）与每次回滚前都会留档一次，供 /forms/{formId}/history
 * 列表与 rollback 使用。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_form_history")
public class SysFormHistory extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 归属表单 ID */
    private Long formId;

    /** 快照对应的表单版本号 */
    private Integer version;

    /** Schema 整包快照（JSON：config + items） */
    private String schemaJson;

    /** 发布人账号（快照） */
    private String publishBy;

    /** 发布时间 */
    private LocalDateTime publishTime;
}
