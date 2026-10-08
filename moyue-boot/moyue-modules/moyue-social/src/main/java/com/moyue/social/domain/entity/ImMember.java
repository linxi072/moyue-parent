package com.moyue.social.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * IM 会话成员实体（含已读位点）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_im_member")
public class ImMember extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private Long conversationId;

    /** 成员用户 ID */
    private Long userId;

    /** 角色：1 普通 / 2 管理员 */
    private Integer role;

    /** 是否免扰：0 否 / 1 是 */
    private Integer muted;

    /** 已读位点（最新已读消息 ID） */
    private Long lastReadMessageId;
}
