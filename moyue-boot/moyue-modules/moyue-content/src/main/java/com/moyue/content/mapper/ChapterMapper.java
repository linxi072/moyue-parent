package com.moyue.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.content.domain.entity.Chapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 章节 Mapper。
 *
 * @author moyue
 */
@Mapper
public interface ChapterMapper extends BaseMapper<Chapter> {

    /** 该作品当前最大章节序号（无章节返回 null） */
    @Select("SELECT MAX(chapter_no) FROM moyue_chapter WHERE book_id = #{bookId} AND is_deleted = 0")
    Integer selectMaxNo(@Param("bookId") Long bookId);

    /** 按作品 + 序号取章节（用于排序互换） */
    @Select("SELECT * FROM moyue_chapter WHERE book_id = #{bookId} AND chapter_no = #{no} AND is_deleted = 0 LIMIT 1")
    Chapter selectByNo(@Param("bookId") Long bookId, @Param("no") int no);
}
