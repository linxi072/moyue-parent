package com.moyue.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.message.entity.NoticeEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 站内通知 Mapper。
 *
 * <p>P2-14 由 moyue-social 整包迁入 moyue-message，包名零变更。</p>
 *
 * <p>notice 表无 {@code @TableLogic}，查询须显式 {@code is_deleted = 0}，
 * 以免把已手动置删的行一并查出（保留现有手动 setIsDeleted 行为，避免回归）。</p>
 */
public interface NoticeMapper extends BaseMapper<NoticeEntity> {

    /**
     * 收件箱站内信查询：按用户、可选类型、可选仅未读过滤，显式排除已删行，按创建时间倒序。
     * 复用 MyBatis-Plus 分页拦截器（首参为 {@link Page} 时自动注入 LIMIT）。
     *
     * @param page       分页参数（本服务聚合后再做内存分页，通常传大页拉全量）
     * @param userId     接收用户 ID
     * @param type      类型筛选（可空：1 系统 / 2 互动 / 3 审核）
     * @param unreadOnly true 时仅未读
     */
    @Select({
            "<script>",
            "SELECT * FROM notice WHERE user_id = #{userId} AND is_deleted = 0",
            "<if test='type != null'> AND type = #{type} </if>",
            "<if test='unreadOnly'> AND is_read = 0 </if>",
            "ORDER BY create_time DESC",
            "</script>"
    })
    IPage<NoticeEntity> selectInbox(Page<NoticeEntity> page,
                                    @Param("userId") Long userId,
                                    @Param("type") Integer type,
                                    @Param("unreadOnly") boolean unreadOnly);

    /**
     * 未读计数：按用户与类型集合（1 系统 / 2 互动）统计未删、未读条数。
     *
     * @param userId 接收用户 ID
     * @param types  类型集合
     * @return 未读条数
     */
    @Select({
            "<script>",
            "SELECT COUNT(1) FROM notice WHERE user_id = #{userId} AND is_deleted = 0 AND is_read = 0 AND type IN",
            "<foreach collection='types' item='t' open='(' separator=',' close=')'> #{t} </foreach>",
            "</script>"
    })
    int countUnread(@Param("userId") Long userId, @Param("types") List<Integer> types);
}
