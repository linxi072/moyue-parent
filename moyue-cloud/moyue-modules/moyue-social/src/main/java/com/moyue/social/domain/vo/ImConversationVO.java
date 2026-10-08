package com.moyue.social.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话视图（聚合成员与未读数）。
 *
 * @author moyue
 */
@Data
@Builder
public class ImConversationVO {

    private Long id;
    private Integer type;
    private String title;
    private Long ownerId;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private List<Long> memberIds;
    /** 当前用户在该会话的未读消息数 */
    private Long unreadCount;
    /** 会话禁用：0 正常 / 1 禁用（运营端治理） */
    private Integer disabled;
}
