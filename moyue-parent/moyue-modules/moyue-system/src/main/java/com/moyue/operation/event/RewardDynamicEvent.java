package com.moyue.operation.event;

import org.springframework.context.ApplicationEvent;

/**
 * 打赏动态领域事件（P2-E）。
 * RewardService 在打赏支付成功并结算稿酬后发布；由 {@link RewardDynamicEventListener} 监听并经
 * {@code DynamicClient} 旁路落 moyue-social 的 {@code user_dynamic}（dynamicType=3），失败仅记 warn。
 */
public class RewardDynamicEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /** 打赏发起者（读者）ID → user.id */
    private final Long actorUserId;

    /** 动态归属作者（被打赏者）ID → user.id */
    private final Long authorId;

    /** 反规范化快照：作者昵称 */
    private final String authorName;

    /** 关联作品 ID → book.id */
    private final Long bookId;

    /** 反规范化快照：作品标题 */
    private final String bookTitle;

    /** 来源业务主键：打赏订单 ID → reward_order.id（同时作为动态 refId，与 refType=2 唯一） */
    private final Long refId;

    public RewardDynamicEvent(Object source, Long actorUserId, Long authorId, String authorName,
                              Long bookId, String bookTitle, Long refId) {
        super(source);
        this.actorUserId = actorUserId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.refId = refId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public Long getRefId() {
        return refId;
    }
}
