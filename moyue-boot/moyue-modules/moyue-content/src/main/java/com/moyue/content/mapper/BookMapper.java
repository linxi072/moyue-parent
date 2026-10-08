package com.moyue.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.content.domain.entity.Book;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作品数据访问。
 *
 * @author moyue
 */
@Mapper
public interface BookMapper extends BaseMapper<Book> {
}
