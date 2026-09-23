package com.moyue.book.category.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.book.category.entity.CategoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类表 Mapper（P2-A 分类服务独立化）。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {
}
