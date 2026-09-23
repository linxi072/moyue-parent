package com.moyue.api.social.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户动态数据传输对象（关注流时间线 / 作者主页动态接口返回）。
 *
 * <p>{@code nextCursor} 为分页级游标字段（见 {@code CursorPageResult}），本 DTO 内仅作预留，
 * 不逐条填充；动态结构由后端按 {@code dynamicType} + 反规范化字段（bookTitle / actorName）渲染。</p>
 */
@Data
public class DynamicDTO implements Serializable {

    /** 动态主键 */
    private Long id;

    /** 动作发起者（打赏者 / 作者本人）ID → user.id */
    private Long actorUserId;

    /** 反规范化快照：动作发起者昵称 */
    private String actorName;

    /** 动态归属作者（进粉丝流的人）ID → user.id */
    private Long authorId;

    /** 反规范化快照：作者昵称 */
    private String authorName;

    /** 关联作品 ID → book.id */
    private Long bookId;

    /** 反规范化快照：作品标题 */
    private String bookTitle;

    /** 动态类型：1 发布新作 / 2 作品完结 / 3 打赏 / 4 关注(预留) */
    private Integer dynamicType;

    /** 可选补充文案 */
    private String summary;

    /** 来源业务主键（bookId / rewardOrderId） */
    private Long refId;

    /** 创建时间（排序键） */
    private LocalDateTime createTime;

    /** 分页级游标（预留，实际游标在 CursorPageResult.nextCursor） */
    private String nextCursor;
}
