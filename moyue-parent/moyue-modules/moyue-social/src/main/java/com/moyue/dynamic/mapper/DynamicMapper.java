package com.moyue.dynamic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.dynamic.entity.DynamicEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户动态 Mapper。
 *
 * <p>动态幂等：uk_dynamic_ref(ref_type, ref_id) 唯一约束保证同来源只一行。
 * countByRef 忽略逻辑删除（原生 SQL 不追加 is_deleted=0）用于探测历史（含已删）记录；
 * upsertByRef 原生 UPDATE 绕过逻辑删除过滤，复活 + 刷新反规范化快照。</p>
 */
public interface DynamicMapper extends BaseMapper<DynamicEntity> {

    /** 是否存在来源记录（忽略 is_deleted，用于幂等探测） */
    @Select("SELECT COUNT(1) FROM user_dynamic WHERE ref_type = #{refType} AND ref_id = #{refId}")
    Integer countByRef(@Param("refType") Integer refType, @Param("refId") Long refId);

    /** 按来源幂等更新：刷新反规范化快照并复活（绕过逻辑删除过滤） */
    @Update("UPDATE user_dynamic SET actor_user_id = #{d.actorUserId}, actor_name = #{d.actorName}, "
            + "author_id = #{d.authorId}, author_name = #{d.authorName}, book_id = #{d.bookId}, "
            + "book_title = #{d.bookTitle}, dynamic_type = #{d.dynamicType}, summary = #{d.summary}, "
            + "is_deleted = 0, update_time = NOW() "
            + "WHERE ref_type = #{d.refType} AND ref_id = #{d.refId}")
    int upsertByRef(@Param("d") DynamicEntity d);
}
