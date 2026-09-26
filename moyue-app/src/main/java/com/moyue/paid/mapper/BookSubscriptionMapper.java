package com.moyue.paid.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.paid.entity.BookSubscriptionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 整本订阅记录 Mapper。
 */
public interface BookSubscriptionMapper extends BaseMapper<BookSubscriptionEntity> {

    /**
     * 查询某用户对某作品的生效中订阅。
     * <p>条件：未删除 + status=1（生效中）+ start_time 已到 + (end_time IS NULL 或大于当前时间)。</p>
     */
    @Select("SELECT * FROM book_subscription WHERE user_id = #{userId} AND book_id = #{bookId} "
            + "AND is_deleted = 0 AND status = 1 AND (start_time IS NULL OR start_time <= NOW()) "
            + "AND (end_time IS NULL OR end_time > NOW())")
    BookSubscriptionEntity selectActive(@Param("userId") Long userId, @Param("bookId") Long bookId);
}
