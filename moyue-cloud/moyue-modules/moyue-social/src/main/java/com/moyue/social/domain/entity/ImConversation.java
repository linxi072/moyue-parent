package com.moyue.social.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * IM 会话实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_im_conversation")
public class ImConversation extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 类型：1 单聊 / 2 群聊 */
    private Integer type;

    /** 群聊名称 */
    private String title;

    /** 创建者 */
    private Long ownerId;

    /** 最近一条消息摘要 */
    private String lastMessage;

    /** 最近消息时间 */
    private LocalDateTime lastMessageTime;

    /** 会话禁用：0 正常 / 1 禁用（运营端治理） */
    private Integer disabled;
}
