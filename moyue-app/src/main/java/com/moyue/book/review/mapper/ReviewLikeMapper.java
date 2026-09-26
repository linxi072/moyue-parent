package com.moyue.book.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.book.review.entity.ReviewLikeEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ReviewLikeMapper extends BaseMapper<ReviewLikeEntity> {

    @Update("UPDATE review_like SET is_deleted = 0, update_time = NOW() "
            + "WHERE review_id = #{reviewId} AND user_id = #{userId}")
    void revive(@Param("reviewId") Long reviewId, @Param("userId") Long userId);
}
