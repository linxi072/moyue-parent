package com.moyue.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.comment.entity.CommentLikeEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 评论点赞 Mapper。
 * 取消点赞走全局逻辑删除（is_deleted=1），但唯一键 uk_comment_user 仍占用，
 * 因此“再次点赞”需把已被逻辑删除的记录复活（置 is_deleted=0）。
 * 该原生 UPDATE 绕开 MyBatis-Plus 自动追加的 is_deleted=0 过滤条件，才能命中已删除行。
 */
public interface CommentLikeMapper extends BaseMapper<CommentLikeEntity> {

    /** 复活被逻辑删除的点赞记录（绕过逻辑删除过滤） */
    @Update("UPDATE comment_like SET is_deleted = 0, update_time = NOW() " +
            "WHERE comment_id = #{commentId} AND user_id = #{userId}")
    int revive(@Param("commentId") Long commentId, @Param("userId") Long userId);
}
