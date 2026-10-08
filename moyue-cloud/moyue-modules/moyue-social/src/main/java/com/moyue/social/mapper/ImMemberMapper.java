package com.moyue.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.social.domain.entity.ImMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * IM 会话成员 Mapper。
 *
 * @author moyue
 */
@Mapper
public interface ImMemberMapper extends BaseMapper<ImMember> {

    /** 会话成员用户 ID 列表 */
    @Select("SELECT user_id FROM moyue_im_member WHERE conversation_id = #{conversationId} AND is_deleted = 0")
    List<Long> selectMemberIds(@Param("conversationId") Long conversationId);

    /** 标记已读位点 */
    @Update("UPDATE moyue_im_member SET last_read_message_id = #{messageId}, update_time = NOW() "
            + "WHERE conversation_id = #{conversationId} AND user_id = #{userId} AND is_deleted = 0")
    int markRead(@Param("conversationId") Long conversationId,
                 @Param("userId") Long userId,
                 @Param("messageId") Long messageId);

    /** 未读消息数（大于已读位点的正常消息） */
    @Select("""
            SELECT COUNT(1) FROM moyue_im_message m
            WHERE m.conversation_id = #{conversationId} AND m.status = 1 AND m.is_deleted = 0
              AND m.id > COALESCE((SELECT last_read_message_id FROM moyue_im_member
                WHERE conversation_id = #{conversationId} AND user_id = #{userId} AND is_deleted = 0), 0)
            """)
    long countUnread(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
