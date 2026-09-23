package com.moyue.read.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.read.entity.BookshelfEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 书架 Mapper。
 * 取消收藏走全局逻辑删除（is_deleted=1），但唯一键 uk_user_book(user_id, book_id) 仍占用，
 * 因此“再次加入书架”需把已被逻辑删除的记录复活（置 is_deleted=0）。
 * 该原生 UPDATE 绕开 MyBatis-Plus 自动追加的 is_deleted=0 过滤条件，才能命中已删除行。
 */
public interface BookshelfMapper extends BaseMapper<BookshelfEntity> {

    /** 复活被逻辑删除的书架记录（绕过逻辑删除过滤） */
    @Update("UPDATE bookshelf SET is_deleted = 0, update_time = NOW() " +
            "WHERE user_id = #{userId} AND book_id = #{bookId}")
    int revive(@Param("userId") Long userId, @Param("bookId") Long bookId);
}
