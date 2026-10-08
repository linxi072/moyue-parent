package com.moyue.message.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.message.domain.dto.query.MessageQuery;
import com.moyue.message.domain.entity.Message;
import com.moyue.message.domain.vo.MessageVO;

import java.util.List;

/**
 * 消息域（站内信）服务。
 *
 * @author moyue
 */
public interface MessageService {

    PageResult<MessageVO> pageMessages(MessageQuery query);

    Long createMessage(Message entity);

    boolean updateMessage(Message entity);

    boolean deleteMessage(Long id);

    /** 指定用户的未读消息数 */
    long unreadCount(Long toUser);

    /**
     * 单发站内信（默认未读，fromUser=0 表示系统）。
     *
     * @return 消息 ID
     */
    Long sendOne(Long toUser, String title, String content, Integer type, String templateCode, String name);

    /**
     * 批量群发站内信（每人一条，均默认未读）。
     *
     * @return 消息 ID 列表
     */
    List<Long> sendBatch(List<Long> toUserIds, String title, String content, Integer type, String templateCode, String name);

    /** 标记已读（按消息 ID 列表） */
    boolean readAll(List<Long> ids);
}
