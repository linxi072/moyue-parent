package com.moyue.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.system.domain.entity.GenTable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 代码生成配置数据访问（含 information_schema 元数据查询）。
 *
 * @author moyue
 */
@Mapper
public interface GenTableMapper extends BaseMapper<GenTable> {

    /**
     * 查询数据库表清单。
     *
     * @param tableName 表名关键字，可空
     * @param schema    库名
     * @return 表清单
     */
    @Select("""
            SELECT table_name AS tableName, table_comment AS tableComment, create_time AS createTime
            FROM information_schema.tables
            WHERE table_schema = #{schema}
              AND table_type = 'BASE TABLE'
              AND (#{tableName} IS NULL OR #{tableName} = '' OR table_name LIKE CONCAT('%', #{tableName}, '%'))
            ORDER BY table_name
            """)
    List<Map<String, Object>> selectDbTables(@Param("tableName") String tableName,
                                             @Param("schema") String schema);

    /**
     * 查询单表元信息。
     */
    @Select("""
            SELECT table_name AS tableName, table_comment AS tableComment
            FROM information_schema.tables
            WHERE table_schema = #{schema} AND table_name = #{tableName}
            """)
    Map<String, Object> selectTableByName(@Param("tableName") String tableName,
                                          @Param("schema") String schema);

    /**
     * 查询表字段元信息。
     */
    @Select("""
            SELECT column_name AS columnName, column_type AS columnType, data_type AS dataType,
                   column_comment AS columnComment, column_key AS columnKey, extra AS extra,
                   is_nullable AS isNullable, ordinal_position AS sort
            FROM information_schema.columns
            WHERE table_schema = #{schema} AND table_name = #{tableName}
            ORDER BY ordinal_position
            """)
    List<Map<String, Object>> selectTableColumns(@Param("tableName") String tableName,
                                                 @Param("schema") String schema);
}
