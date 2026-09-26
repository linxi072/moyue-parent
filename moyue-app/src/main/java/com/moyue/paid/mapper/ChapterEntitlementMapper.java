package com.moyue.paid.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyue.paid.entity.ChapterEntitlementEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 章节解锁记录 Mapper（单章购买）。
 */
public interface ChapterEntitlementMapper extends BaseMapper<ChapterEntitlementEntity> {

    /**
     * 查询某用户对某章节的生效中解锁记录。
     * <p>条件：未删除 + status=1（已解锁）+ (expire_time IS NULL 或大于当前时间)。</p>
     */
    @Select("SELECT * FROM chapter_entitlement WHERE user_id = #{userId} AND chapter_id = #{chapterId} "
            + "AND is_deleted = 0 AND status = 1 AND (expire_time IS NULL OR expire_time > NOW())")
    ChapterEntitlementEntity selectActive(@Param("userId") Long userId, @Param("chapterId") Long chapterId);
}
