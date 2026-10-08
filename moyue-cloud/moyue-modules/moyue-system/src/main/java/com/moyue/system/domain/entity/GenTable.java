package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 代码生成业务表配置实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_table")
public class GenTable extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 表名称 */
    private String tableName;

    /** 表描述 */
    private String tableComment;

    /** 子表名称（主子表模板） */
    private String subTableName;

    /** 子表关联的外键名 */
    private String subTableFkName;

    /** 实体类名称 */
    private String className;

    /** 模板类型：crud / tree / sub */
    private String tplCategory;

    /** 生成包路径 */
    private String packageName;

    /** 生成模块名 */
    private String moduleName;

    /** 生成业务名 */
    private String businessName;

    /** 生成功能名 */
    private String functionName;

    /** 生成功能作者 */
    private String functionAuthor;

    /** 生成方式：0 Zip 下载 / 1 写入自定义路径 */
    private Integer genType;

    /** 自定义生成路径 */
    private String genPath;

    /** 其它生成选项（JSON） */
    private String options;
}
