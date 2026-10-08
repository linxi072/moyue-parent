package com.moyue.social.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 消息视图。
 *
 * @author moyue
 */
@Data
@Builder
public class ImMessageVO {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private String content;
    private Integer type;
    private Integer status;
    private LocalDateTime createTime;
}
