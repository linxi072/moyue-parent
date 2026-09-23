package com.moyue.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.im.entity.ConversationEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会话 Mapper。
 */
public interface ConversationMapper extends BaseMapper<ConversationEntity> {

    /** 查询某用户参与的所有会话（按最近消息时间倒序） */
    @Select("SELECT c.* FROM chat_conversation c JOIN chat_conversation_member m " +
            "ON c.id=m.conversation_id WHERE m.user_id=#{userId} AND c.is_deleted=0 " +
            "ORDER BY c.last_message_time DESC")
    IPage<ConversationEntity> selectByUser(Page<ConversationEntity> page, @Param("userId") Long userId);
}
