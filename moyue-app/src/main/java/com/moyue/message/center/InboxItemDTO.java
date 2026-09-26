package com.moyue.message.center;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一收件箱聚合条目 DTO。
 *
 * <p>将 {@code notice}（站内信）与 {@code announcement}（公告）两类来源归一为同一视图，
 * 供「消息中心」收件箱、未读角标、按类型筛选、已读等能力复用。</p>
 *
 * <p>source 取值：{@code NOTICE}（站内信）/ {@code ANNOUNCEMENT}（公告）。
 * 公告为平台全局广播，不计入未读，故 read 恒为 true。</p>
 */
@Data
public class InboxItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源：NOTICE / ANNOUNCEMENT */
    private String source;

    /** 条目主键（notice.id 或 announcement.id） */
    private Long id;

    /** 类型：1 系统 / 2 互动 / 3 公告 */
    private Integer type;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 是否已读（公告恒为 true） */
    private Boolean read;

    /** 创建/发布时间（用于收件箱时间倒序排序） */
    private LocalDateTime createTime;

    /** 业务类型（可空，预留扩展，当前站内信未使用） */
    private String bizType;

    /** 业务主键（可空，预留扩展，当前站内信未使用） */
    private String bizId;
}
