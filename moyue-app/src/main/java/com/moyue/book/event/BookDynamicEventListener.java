package com.moyue.book.event;

import com.moyue.api.social.client.DynamicClient;
import com.moyue.api.social.dto.DynamicPublishDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 书籍动态事件监听（P2-E）：将书籍发布 / 完结转化为粉丝流动态。
 *
 * <p>经 {@code DynamicClient} 调 social 内部端点落 {@code user_dynamic}；
 * {@code fallbackExecution=true} 保证无事务上下文（createBook / updateBook 非事务方法）时也能触发；
 * 任何异常仅 {@code log.warn}，绝不阻断书城主流程；social 未注册时 DynamicClient 为 null，直接跳过。</p>
 */
@Slf4j
@Component
public class BookDynamicEventListener {

    @Autowired(required = false)
    private DynamicClient dynamicClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookDynamic(BookDynamicEvent event) {
        if (dynamicClient == null || event == null) {
            return;
        }
        try {
            DynamicPublishDTO dto = new DynamicPublishDTO();
            dto.setActorUserId(event.getAuthorId());
            dto.setActorName(event.getAuthorName());
            dto.setAuthorId(event.getAuthorId());
            dto.setAuthorName(event.getAuthorName());
            dto.setBookId(event.getBookId());
            dto.setBookTitle(event.getBookTitle());
            dto.setDynamicType(event.getDynamicType());
            dto.setRefId(event.getBookId());
            dto.setRefType(1);
            dynamicClient.publish(dto);
        } catch (Exception ex) {
            log.warn("发布书籍动态失败 bookId={} type={}, err={}",
                    event.getBookId(), event.getDynamicType(), ex.getMessage());
        }
    }
}
