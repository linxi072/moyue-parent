package com.moyue.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.social.domain.entity.CommentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 评论点赞记录 Mapper。
 *
 * @author moyue
 */
@Mapper
public interface CommentLikeMapper extends BaseMapper<CommentLike> {

    /**
     * 查询已存在的点赞行（<b>忽略逻辑删除</b>）。
     *
     * <p>MP 的 {@code @TableLogic} 会把 is_deleted=1 的行过滤掉，导致「取消点赞后再次点赞」
     * 时查不到旧行而误走 insert，撞唯一键。此处用原生 SQL 探测全量，
     * 由调用方决定复活还是插入。
     *
     * @param commentId 评论 ID
     * @param userId    用户 ID
     * @return 行数（0 或 1）
     */
    @Select("SELECT COUNT(1) FROM moyue_comment_like "
            + "WHERE comment_id = #{commentId} AND user_id = #{userId}")
    int countAny(@Param("commentId") Long commentId, @Param("userId") Long userId);
}
