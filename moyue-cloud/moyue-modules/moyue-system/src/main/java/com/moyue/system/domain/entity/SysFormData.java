package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

import java.io.Serial;

/**
 * 在线构建器提交数据实体（缺口 G-7 补充表）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_form_data")
public class SysFormData extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 归属表单 ID */
    private Long formId;

    /** 表单标识，冗余便于检索 */
    private String formKey;

    /** 提交内容（JSON：item_key -> value） */
    private String dataJson;

    /** 提交人 ID，匿名表单为空 */
    private Long submitBy;

    /** 提交人账号（快照） */
    private String submitName;

    /** 提交 IP */
    private String submitIp;

    /** 提交时间 */
    private java.time.LocalDateTime submitTime;

    /** 状态：0 待清理 / 1 正常 */
    private Integer status;
}
