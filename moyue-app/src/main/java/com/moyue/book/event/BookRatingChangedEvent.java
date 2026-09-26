package com.moyue.book.event;

import org.springframework.context.ApplicationEvent;

public class BookRatingChangedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;
    private final Long bookId;

    public BookRatingChangedEvent(Object source, Long bookId) {
        super(source);
        this.bookId = bookId;
    }

    public Long getBookId() {
        return bookId;
    }
}
