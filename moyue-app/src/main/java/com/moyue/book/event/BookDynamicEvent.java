package com.moyue.book.event;

import org.springframework.context.ApplicationEvent;

/**
 * 书籍动态领域事件（P2-E）。
 * BookService 在作品发布 / 完结写库后发布；由 {@link BookDynamicEventListener} 监听并经
 * {@code DynamicClient} 旁路落 moyue-social 的 {@code user_dynamic}，失败仅记 warn，不阻断书城主流程。
 */
public class BookDynamicEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /** 作品 ID → book.id（同时作为动态 refId） */
    private final Long bookId;

    /** 反规范化快照：作品标题 */
    private final String bookTitle;

    /** 作者（也是动作发起者）ID → user.id */
    private final Long authorId;

    /** 反规范化快照：作者昵称 */
    private final String authorName;

    /** 动态类型：1 发布新作 / 2 作品完结 */
    private final int dynamicType;

    public BookDynamicEvent(Object source, Long bookId, String bookTitle, Long authorId,
                            String authorName, int dynamicType) {
        super(source);
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.authorId = authorId;
        this.authorName = authorName;
        this.dynamicType = dynamicType;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public int getDynamicType() {
        return dynamicType;
    }
}
