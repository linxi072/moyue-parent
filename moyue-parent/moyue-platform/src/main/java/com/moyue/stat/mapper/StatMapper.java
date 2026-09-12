package com.moyue.stat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 统计聚合 Mapper（只读，不建实体）。
 * 使用注解 SQL 对公共库表做聚合计数，不触碰公共模块的 Flyway 迁移。
 *
 * <h3>表/列出处（均来自 moyue-common 的 Flyway 迁移）</h3>
 * <ul>
 *   <li>{@code user}（V1）：id / nickname / role / is_deleted</li>
 *   <li>{@code book}（V1）：id / author_id / word_count / click_count / is_deleted</li>
 *   <li>{@code chapter}（V1）：id / book_id / status / is_deleted</li>
 *   <li>{@code bookshelf}（V1）：user_id / update_time / is_deleted</li>
 *   <li>{@code comment}（V1）：user_id / create_time / is_deleted</li>
 *   <li>{@code reward_order}（V1 建表，V6 补 is_deleted）：user_id / book_id / amount / status / pay_time / is_deleted</li>
 *   <li>{@code points_order}（V4）：user_id / status / is_deleted</li>
 * </ul>
 * 注意：author_income 无 is_deleted 列、reward_order.amount 单位已是「元」，SQL 中不得臆造字段。
 */
@Mapper
public interface StatMapper {

    @Select("SELECT COUNT(*) FROM user WHERE is_deleted = 0")
    long countUser();

    @Select("SELECT COUNT(*) FROM book WHERE is_deleted = 0")
    long countBook();

    @Select("SELECT COUNT(*) FROM comment WHERE is_deleted = 0")
    long countComment();

    // ------------------------------------------------------------------
    // 1) 作者维度统计
    // ------------------------------------------------------------------

    /**
     * 作者维度分页统计。
     *
     * <p>口径：以 {@code user} 为驱动表，候选作者 = role=2（作者角色，V1 字典：1读者/2作者/3管理员）
     * 或至少拥有一本未逻辑删除作品的账户；三个 LEFT JOIN 子查询各自独立聚合，
     * 避免 book×chapter×reward_order 笛卡尔积导致 SUM 放大。</p>
     *
     * <p>keyword 为 null/空时不参与过滤（{@code #{keyword} IS NULL} 短路），非空时走
     * {@code LIKE CONCAT('%', #{keyword}, '%')}，全文拼接在 SQL 侧完成，不走字符串拼接防注入。</p>
     *
     * @param keyword 昵称模糊匹配关键字，可为空
     * @param offset  分页起始下标（page-1）*size
     * @param size    每页大小
     * @return 每行：authorId / nickname / bookCount / totalWordCount / totalClickCount
     *         / rewardAmount（元） / publishedChapterCount
     */
    @Select("SELECT u.id AS authorId, u.nickname AS nickname, " +
            "       COALESCE(bk.bookCount, 0) AS bookCount, " +
            "       COALESCE(bk.wordCount, 0) AS totalWordCount, " +
            "       COALESCE(bk.clickCount, 0) AS totalClickCount, " +
            "       COALESCE(rw.rewardAmount, 0) AS rewardAmount, " +
            "       COALESCE(cp.publishedChapterCount, 0) AS publishedChapterCount " +
            "FROM `user` u " +
            "LEFT JOIN (SELECT author_id, COUNT(*) AS bookCount, " +
            "                  SUM(word_count) AS wordCount, SUM(click_count) AS clickCount " +
            "           FROM `book` WHERE is_deleted = 0 GROUP BY author_id) bk ON bk.author_id = u.id " +
            "LEFT JOIN (SELECT b2.author_id AS author_id, COUNT(*) AS publishedChapterCount " +
            "           FROM `chapter` c JOIN `book` b2 ON b2.id = c.book_id " +
            "           WHERE c.is_deleted = 0 AND c.status = 2 AND b2.is_deleted = 0 " +
            "           GROUP BY b2.author_id) cp ON cp.author_id = u.id " +
            "LEFT JOIN (SELECT b3.author_id AS author_id, SUM(o.amount) AS rewardAmount " +
            "           FROM `reward_order` o JOIN `book` b3 ON b3.id = o.book_id " +
            "           WHERE o.is_deleted = 0 AND o.status = 1 AND b3.is_deleted = 0 " +
            "           GROUP BY b3.author_id) rw ON rw.author_id = u.id " +
            "WHERE u.is_deleted = 0 " +
            "  AND (u.role = 2 OR bk.author_id IS NOT NULL) " +
            "  AND (#{keyword} IS NULL OR u.nickname LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY totalClickCount DESC, authorId ASC " +
            "LIMIT #{offset}, #{size}")
    List<Map<String, Object>> selectAuthorStats(@Param("keyword") String keyword,
                                               @Param("offset") long offset,
                                               @Param("size") int size);

    /**
     * 作者维度统计的总记录数，过滤条件必须与 {@link #selectAuthorStats} 完全一致。
     *
     * @param keyword 昵称模糊匹配关键字，可为空
     * @return 命中行数
     */
    @Select("SELECT COUNT(*) FROM ( " +
            "  SELECT u.id FROM `user` u " +
            "  LEFT JOIN (SELECT DISTINCT author_id FROM `book` WHERE is_deleted = 0) bk " +
            "         ON bk.author_id = u.id " +
            "  WHERE u.is_deleted = 0 " +
            "    AND (u.role = 2 OR bk.author_id IS NOT NULL) " +
            "    AND (#{keyword} IS NULL OR u.nickname LIKE CONCAT('%', #{keyword}, '%')) " +
            ") t")
    long countAuthorStats(@Param("keyword") String keyword);

    // ------------------------------------------------------------------
    // 2) 留存漏斗
    // ------------------------------------------------------------------

    /**
     * 激活用户数：有过阅读行为的用户（去重）。
     *
     * <p>口径受限说明：全库不存在 read_record / 阅读流水表，最接近阅读行为的载体是
     * {@code bookshelf}（V1，含 last_chapter_id 最后阅读章节），因此以「存在未删除书架记录」
     * 作为已产生阅读行为的判定。</p>
     *
     * @return 去重用户数
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM `bookshelf` WHERE is_deleted = 0")
    long countActivatedUser();

    /**
     * 付费用户数：有过成功打赏或已完成积分兑换的用户（去重）。
     *
     * <p>reward_order.status=1 已支付（V1 字典：0待支付/1已支付/2已关闭/3已退款）；
     * points_order.status=1 已兑换（V4 字典：0待兑换/1已兑换/2已取消）。
     * UNION 自带跨集合去重。</p>
     *
     * @return 去重用户数
     */
    @Select("SELECT COUNT(*) FROM ( " +
            "  SELECT user_id FROM `reward_order` WHERE is_deleted = 0 AND status = 1 " +
            "  UNION " +
            "  SELECT user_id FROM `points_order` WHERE is_deleted = 0 AND status = 1 " +
            ") t")
    long countPayingUser();

    /**
     * 近 7 日活跃用户数（去重）。
     *
     * <p>口径受限说明：{@code user} 表没有 last_login_time 之类的登录时间列（已核对 V1~V10，
     * 确认不存在），因此改为「近 7 日有任意业务行为」：书架有更新（bookshelf.update_time）、
     * 有新评论（comment.create_time）或有成功打赏（reward_order.pay_time）任一满足即计入。</p>
     *
     * @return 去重用户数
     */
    @Select("SELECT COUNT(*) FROM ( " +
            "  SELECT user_id FROM `bookshelf` " +
            "   WHERE is_deleted = 0 AND update_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "  UNION " +
            "  SELECT user_id FROM `comment` " +
            "   WHERE is_deleted = 0 AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "  UNION " +
            "  SELECT user_id FROM `reward_order` " +
            "   WHERE is_deleted = 0 AND status = 1 AND pay_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            ") t")
    long countActiveUser7d();
}
