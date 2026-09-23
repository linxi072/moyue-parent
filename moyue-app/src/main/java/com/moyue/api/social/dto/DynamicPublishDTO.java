package com.moyue.api.social.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 内部发布动态载荷（经 {@code DynamicClient} Feign 由 content / system 调用 social 内部端点）。
 *
 * <p>反规范化快照（actorName / authorName / bookTitle）由发布方在事件侧填充，
 * social 不回查来源服务（content / system），保证跨模块旁路调用零侵入主流程。</p>
 */
@Data
public class DynamicPublishDTO implements Serializable {

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

    /** 来源类型：1=book发布 / 2=reward订单 */
    private Integer refType;
}
