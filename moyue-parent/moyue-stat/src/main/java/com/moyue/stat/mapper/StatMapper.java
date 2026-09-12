package com.moyue.stat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 统计聚合 Mapper（只读，不建实体）。
 * 使用注解 SQL 对公共库表做聚合计数，不触碰公共模块的 Flyway 迁移。
 */
@Mapper
public interface StatMapper {

    @Select("SELECT COUNT(*) FROM user WHERE is_deleted = 0")
    long countUser();

    @Select("SELECT COUNT(*) FROM book WHERE is_deleted = 0")
    long countBook();

    @Select("SELECT COUNT(*) FROM comment WHERE is_deleted = 0")
    long countComment();
}
