package com.moyue.message.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 站内信实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_message")
public class Message extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发送人 ID（系统消息为 0） */
    private Long fromUser;

    /** 接收人 ID */
    private Long toUser;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 类型：1 系统 / 2 活动 / 3 私信 */
    private Integer type;

    /** 已读标记：0 未读 / 1 已读 */
    private Integer readFlag;
}
