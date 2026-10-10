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
     * 当前用户的消息分页（C 端收件箱）。
     *
     * <p>强制按 {@code userId} 限定接收人，忽略调用方传入的 toUser，避免越权查看他人消息。
     */
    PageResult<MessageVO> pageMyMessages(Long userId, MessageQuery query);

    /**
     * 标记本人指定消息已读（C 端）。
     *
     * <p>更新按 {@code toUser = userId} 限定，即使 {@code ids} 含他人消息 ID 也不会生效。
     *
     * @return 是否产生更新
     */
    boolean readMine(Long userId, List<Long> ids);

    /**
     * 标记本人全部消息已读（C 端）。
     *
     * @return 是否产生更新
     */
    boolean readAllMine(Long userId);

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
