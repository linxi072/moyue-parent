package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 在线构建器字段项实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_form_item")
public class SysFormItem extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 归属表单 ID */
    private Long formId;

    /** 字段名称（展示用） */
    private String itemName;

    /** 字段标识（提交时的 key） */
    private String itemKey;

    /** 控件类型 */
    private String itemType;

    /** 默认值 */
    private String defaultValue;

    /** 输入提示 */
    private String placeholder;

    /** 选项与校验规则（JSON） */
    private String options;

    /** 是否必填：0 否 / 1 是 */
    private Integer required;

    /** 排序 */
    private Integer sort;
}
