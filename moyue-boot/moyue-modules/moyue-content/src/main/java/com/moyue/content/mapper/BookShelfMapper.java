package com.moyue.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.content.domain.entity.BookShelf;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 书架 Mapper。
 *
 * @author moyue
 */
@Mapper
public interface BookShelfMapper extends BaseMapper<BookShelf> {

    /**
     * 查询读者书架（联合作品基础信息），仅未删除行。
     *
     * <p>注解 SQL 不支持 XML 动态标签，故用原生条件表达式：bookTitle 为空时
     * 通过 {@code (#{bookTitle} IS NULL OR #{bookTitle} = '')} 短路，保持单一静态 SQL。
     *
     * @param userId    读者 ID
     * @param bookTitle 作品名模糊（可空）
     * @return 书架视图行
     */
    @Select("""
            SELECT s.id, s.book_id AS bookId, b.title AS bookTitle, b.cover_url AS coverUrl,
                   s.last_chapter_no AS lastChapterNo, s.create_time AS createTime
            FROM moyue_bookshelf s
            LEFT JOIN moyue_book b ON b.id = s.book_id AND b.is_deleted = 0
            WHERE s.user_id = #{userId} AND s.is_deleted = 0
              AND (#{bookTitle} IS NULL OR #{bookTitle} = '' OR b.title LIKE CONCAT('%', #{bookTitle}, '%'))
            ORDER BY s.create_time DESC
            """)
    List<com.moyue.content.domain.vo.BookShelfVO> selectShelf(@Param("userId") Long userId,
                                                              @Param("bookTitle") String bookTitle);

    /**
     * 复活被逻辑删除的书架行（重新加入时调用）。
     *
     * @param userId 读者 ID
     * @param bookId 作品 ID
     * @return 影响行数
     */
    @Update("UPDATE moyue_bookshelf SET is_deleted = 0, update_time = NOW() "
            + "WHERE user_id = #{userId} AND book_id = #{bookId} AND is_deleted = 1")
    int revive(@Param("userId") Long userId, @Param("bookId") Long bookId);

    /**
     * 管理端全局书架查询（联合作品基础信息），仅未删除行，支持作品名模糊。
     *
     * @param bookTitle 作品名模糊（可空）
     * @return 书架视图行（含 userId）
     */
    @Select("""
            SELECT s.id, s.user_id AS userId, s.book_id AS bookId, b.title AS bookTitle, b.cover_url AS coverUrl,
                   s.last_chapter_no AS lastChapterNo, s.create_time AS createTime
            FROM moyue_bookshelf s
            LEFT JOIN moyue_book b ON b.id = s.book_id AND b.is_deleted = 0
            WHERE s.is_deleted = 0
              AND (#{bookTitle} IS NULL OR #{bookTitle} = '' OR b.title LIKE CONCAT('%', #{bookTitle}, '%'))
            ORDER BY s.create_time DESC
            """)
    List<com.moyue.content.domain.vo.BookShelfVO> selectAdminShelf(@Param("bookTitle") String bookTitle);
}
