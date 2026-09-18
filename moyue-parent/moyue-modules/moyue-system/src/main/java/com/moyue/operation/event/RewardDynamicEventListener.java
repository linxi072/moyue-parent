package com.moyue.operation.event;

import com.moyue.api.social.client.DynamicClient;
import com.moyue.api.social.dto.DynamicPublishDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 打赏动态事件监听（P2-E）：将打赏转化为粉丝流动态（dynamicType=3）。
 *
 * <p>经 {@code DynamicClient} 调 social 内部端点落 {@code user_dynamic}；
 * {@code fallbackExecution=true} 保证事务提交后触发；任何异常仅 {@code log.warn}，
 * 绝不阻断打赏支付主流程；social 未注册时 DynamicClient 为 null，直接跳过。</p>
 */
@Slf4j
@Component
public class RewardDynamicEventListener {

    @Autowired(required = false)
    private DynamicClient dynamicClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReward(RewardDynamicEvent event) {
        if (dynamicClient == null || event == null) {
            return;
        }
        try {
            DynamicPublishDTO dto = new DynamicPublishDTO();
            dto.setActorUserId(event.getActorUserId());
            dto.setActorName(null);
            dto.setAuthorId(event.getAuthorId());
            dto.setAuthorName(event.getAuthorName());
            dto.setBookId(event.getBookId());
            dto.setBookTitle(event.getBookTitle());
            dto.setDynamicType(3);
            dto.setRefId(event.getRefId());
            dto.setRefType(2);
            dynamicClient.publish(dto);
        } catch (Exception ex) {
            log.warn("发布打赏动态失败 orderId={}, err={}", event.getRefId(), ex.getMessage());
        }
    }
}
