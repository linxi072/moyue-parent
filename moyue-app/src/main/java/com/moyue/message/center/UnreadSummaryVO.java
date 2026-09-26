package com.moyue.message.center;

import lombok.Data;

import java.io.Serializable;

/**
 * 未读汇总 VO（消息中心角标）。
 *
 * <p>公告为全局广播、不计入未读，故未读仅含「系统（1）」与「互动（2）」两类。
 * totalUnread = systemUnread + interactionUnread。</p>
 */
@Data
public class UnreadSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 未读总数（系统 + 互动） */
    private Integer totalUnread;

    /** 系统消息未读 */
    private Integer systemUnread;

    /** 互动消息未读 */
    private Integer interactionUnread;
}
