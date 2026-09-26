package com.moyue.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.book.entity.BookEntity;
import org.apache.ibatis.annotations.Update;

/**
 * 作品 Mapper。
 */
public interface BookMapper extends BaseMapper<BookEntity> {

    @Update("UPDATE book SET click_count = click_count + 1 WHERE id = #{id} AND is_deleted = 0")
    void incrementClickCount(Long id);
}
