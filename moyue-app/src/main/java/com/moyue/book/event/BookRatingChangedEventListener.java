package com.moyue.book.event;

import com.moyue.book.service.BookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 评分变更事件监听：书评提交 / 删除 / 审核回写导致评分聚合变化后，刷新书籍搜索索引。
 *
 * <p>经 {@code BookService.refreshIndex} 旁路刷新搜索索引；
 * {@code fallbackExecution=true} 保证无事务上下文时也能触发；
 * 任何异常仅 {@code log.warn}，绝不阻断书评主流程；bookService 未注册（刷新能力未就绪）时为 null，直接跳过。</p>
 */
@Slf4j
@Component
public class BookRatingChangedEventListener {

    @Autowired(required = false)
    private BookService bookService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookRatingChanged(BookRatingChangedEvent event) {
        if (event == null) {
            return;
        }
        try {
            bookService.refreshIndex(event.getBookId());
        } catch (Exception ex) {
            log.warn("评分变更刷新索引失败 bookId={}, err={}", event.getBookId(), ex.getMessage());
        }
    }
}
