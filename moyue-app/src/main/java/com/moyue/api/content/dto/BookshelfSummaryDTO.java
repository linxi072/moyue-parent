package com.moyue.api.content.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 书架概要 DTO（跨服务共享）：仅暴露推荐画像所需的书 ID，避免在 Feign 契约中
 * 透传 {@link com.moyue.read.entity.BookshelfEntity} 内部字段。
 */
@Data
public class BookshelfSummaryDTO implements Serializable {

    /** 书籍 ID → book.id */
    private Long bookId;
}
