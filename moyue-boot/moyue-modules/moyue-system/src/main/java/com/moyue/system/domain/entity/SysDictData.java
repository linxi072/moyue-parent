package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 字典数据表实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 字典类型 */
    private String dictType;

    /** 字典标签（展示值） */
    private String dictLabel;

    /** 字典键值（存储值） */
    private String dictValue;

    /** 显示排序 */
    private Integer dictSort;

    /** 是否默认：0 否 / 1 是 */
    private Integer isDefault;

    /** 状态：0 停用 / 1 正常 */
    private Integer status;
}
