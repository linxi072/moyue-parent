package com.moyue.book.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.book.review.entity.ReviewEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ReviewMapper extends BaseMapper<ReviewEntity> {

    @Select("SELECT COALESCE(AVG(score), 0) AS avg, COUNT(*) AS cnt FROM book_review "
            + "WHERE book_id = #{bookId} AND is_deleted = 0 AND status = 1")
    ReviewSummary selectSummary(@Param("bookId") Long bookId);
}
