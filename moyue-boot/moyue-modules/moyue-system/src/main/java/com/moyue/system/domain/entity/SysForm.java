package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 在线构建器表单实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_form")
public class SysForm extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 表单名称 */
    private String formName;

    /** 表单标识，唯一 */
    private String formKey;

    /** 表单描述 */
    private String formDesc;

    /** 表单整体配置（JSON） */
    private String config;

    /** 状态：0 草稿 / 1 已发布 / 2 已下线 */
    private Integer status;

    /** 版本号，每次发布 +1 */
    private Integer version;
}
