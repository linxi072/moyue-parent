package com.moyue.follow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.follow.entity.FollowEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 关注关系 Mapper。
 *
 * <p>重关注（uk_follow_pair 冲突）需复活被逻辑删除的旧记录：原生 UPDATE 绕过
 * MyBatis-Plus 自动追加的 is_deleted=0 过滤条件，才能命中已删除行。</p>
 */
public interface FollowMapper extends BaseMapper<FollowEntity> {

    /** 复活被逻辑删除的关注记录（绕过逻辑删除过滤） */
    @Update("UPDATE follow_relation SET is_deleted = 0, update_time = NOW() " +
            "WHERE fan_id = #{fanId} AND author_id = #{authorId}")
    int revive(@Param("fanId") Long fanId, @Param("authorId") Long authorId);
}
