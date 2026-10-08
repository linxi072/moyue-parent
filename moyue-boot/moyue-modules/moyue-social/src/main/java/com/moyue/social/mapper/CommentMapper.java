package com.moyue.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.social.domain.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 评论 Mapper。
 *
 * @author moyue
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /** 查询用户是否已赞（仅未删除行，用于幂等判定） */
    @Select("SELECT COUNT(1) FROM moyue_comment_like "
            + "WHERE comment_id = #{commentId} AND user_id = #{userId} AND is_deleted = 0")
    int countLike(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /**
     * 复活被逻辑删除的点赞记录（取消点赞后再次点赞时调用）。
     *
     * <p>唯一键 {@code uk_comment_like} 不含 is_deleted，取消点赞仅置 is_deleted=1，
     * 直接 insert 会撞唯一键；与书架同策略，复活原行。
     */
    @Update("UPDATE moyue_comment_like SET is_deleted = 0, update_time = NOW() "
            + "WHERE comment_id = #{commentId} AND user_id = #{userId} AND is_deleted = 1")
    int reviveLike(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /** 取消点赞：逻辑删除点赞行（原生 SQL，不依赖 MP 逻辑删除插件） */
    @Update("UPDATE moyue_comment_like SET is_deleted = 1, update_time = NOW() "
            + "WHERE comment_id = #{commentId} AND user_id = #{userId} AND is_deleted = 0")
    int discardLike(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /** 点赞 +1 */
    @Update("UPDATE moyue_comment SET like_count = like_count + 1 WHERE id = #{commentId}")
    int incLike(@Param("commentId") Long commentId);

    /** 点赞 -1（下限 0） */
    @Update("UPDATE moyue_comment SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{commentId}")
    int decLike(@Param("commentId") Long commentId);
}
