package com.moyue.social.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.PageQuery;
import com.moyue.social.domain.vo.ImConversationVO;
import com.moyue.social.domain.vo.ImMessageVO;

import java.util.List;

/**
 * IM 服务（C 端实时通讯 + 运营端治理）。
 *
 * @author moyue
 */
public interface ImService {

    /** 创建会话（单聊/群聊），自动建成员 */
    Long createConversation(Integer type, Long ownerId, List<Long> memberIds, String title);

    /** 我的会话列表（含成员与未读数） */
    List<ImConversationVO> listConversations(Long userId);

    /** 会话详情（含成员） */
    ImConversationVO getConversation(Long conversationId);

    /** 会话消息分页（按 id 游标） */
    List<ImMessageVO> listMessages(Long conversationId, Long userId, Long cursor, int size);

    /** 发送消息：落库 + 更新会话摘要 + WS 定向广播 */
    ImMessageVO send(Long conversationId, Long senderId, String content, Integer type);

    /** 撤回消息（仅发送者，status=2） */
    void recall(Long userId, Long messageId);

    /** 标记会话已读（更新成员已读位点） */
    void markRead(Long conversationId, Long userId);

    // ============ 运营端（管理后台） ============

    /** 会话分页（运营全局视角，无视成员身份） */
    PageResult<ImConversationVO> adminPageConversations(PageQuery query);

    /** 会话消息列表（运营查看，无视成员身份） */
    List<ImMessageVO> adminListMessages(Long conversationId, int size);

    /** 禁用 / 启用会话 */
    boolean disableConversation(Long conversationId, int disabled);
}
