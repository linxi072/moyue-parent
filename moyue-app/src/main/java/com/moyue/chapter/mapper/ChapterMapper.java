package com.moyue.chapter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.chapter.entity.ChapterEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 章节 Mapper。
 *
 * <p>P0 作者创作中心：新增 {@code countByBookIdAndStatus(s)} 用于看板聚合“章节创作态分布”
 * （草稿/已发布/已驳回/定时待发布）。自定义 {@code @Select} 不触发 MP 逻辑删除拦截器，
 * 故 COUNT SQL 必须显式 {@code is_deleted=0}。</p>
 */
public interface ChapterMapper extends BaseMapper<ChapterEntity> {

    /** 单本作品按状态的章节数（显式过滤逻辑删除） */
    @Select("SELECT COUNT(1) FROM chapter WHERE book_id=#{bookId} AND status=#{status} AND is_deleted=0")
    int countByBookIdAndStatus(@Param("bookId") Long bookId, @Param("status") int status);

    /** 批量作品按状态的章节数合计（显式过滤逻辑删除） */
    @Select("<script>SELECT COUNT(1) FROM chapter WHERE status=#{status} AND is_deleted=0 AND book_id IN " +
            "<foreach collection='bookIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int countByBookIdsAndStatus(@Param("bookIds") List<Long> bookIds, @Param("status") int status);
}
