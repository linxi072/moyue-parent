package com.moyue.social.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * IM 消息实体。
 *
 * <p>生命周期 status：1 正常 / 2 已撤回 / 0 已删除。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_im_message")
public class ImMessage extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private Long conversationId;

    /** 发送者 */
    private Long senderId;

    /** 消息内容 */
    private String content;

    /** 类型：1 文本 / 2 图片 / 3 系统 */
    private Integer type;

    /** 状态：1 正常 / 2 已撤回 / 0 已删 */
    private Integer status;
}
