package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 代码生成字段配置实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_table_column")
public class GenTableColumn extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 归属表配置 ID */
    private Long tableId;

    /** 列名称 */
    private String columnName;

    /** 列描述 */
    private String columnComment;

    /** 列类型 */
    private String columnType;

    /** Java 类型 */
    private String javaType;

    /** Java 字段名 */
    private String javaField;

    /** 是否主键：0 否 / 1 是 */
    private Integer isPk;

    /** 是否自增 */
    private Integer isIncrement;

    /** 是否必填 */
    private Integer isRequired;

    /** 是否新增字段 */
    private Integer isInsert;

    /** 是否编辑字段 */
    private Integer isEdit;

    /** 是否列表字段 */
    private Integer isList;

    /** 是否查询字段 */
    private Integer isQuery;

    /** 查询方式 EQ / NE / LIKE / BETWEEN 等 */
    private String queryType;

    /** 表单控件类型 */
    private String htmlType;

    /** 字典类型 */
    private String dictType;

    /** 排序 */
    private Integer sort;

    /**
     * TypeScript 类型（仅代码生成模板使用，非数据库列）。
     *
     * <p>刻意用派生方法而非字段：避免 MyBatis-Plus 把它当成待持久化的列。
     *
     * @return 对应的 TS 类型字面量
     */
    public String getTsType() {
        if (javaType == null) {
            return "string";
        }
        return switch (javaType) {
            case "Long", "Integer", "java.math.BigDecimal" -> "number";
            default -> "string";
        };
    }
}
