package com.moyue.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典数据实体，映射 sys_dict_data 表（V14）。
 * <p>字典数据随类型删除而物理删除，不做逻辑删除标记。</p>
 */
@Data
@TableName("sys_dict_data")
public class SysDictDataEntity {

    /** 字典数据主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属字典类型编码 → sys_dict_type.dict_type */
    private String dictType;

    /** 字典标签（展示用） */
    private String dictLabel;

    /** 字典键值（存储用） */
    private String dictValue;

    /** 显示顺序 */
    private Integer dictSort;

    /** 是否默认：0 否 / 1 是 */
    private Integer isDefault;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
