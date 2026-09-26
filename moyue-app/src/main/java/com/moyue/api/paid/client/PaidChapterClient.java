package com.moyue.api.paid.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.paid.entity.BookSubscriptionEntity;
import com.moyue.paid.entity.ChapterEntitlementEntity;
import com.moyue.paid.service.PaidChapterService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 付费章节服务进程内适配器（monolith 版）。
 * 其它域（如阅读器计费提示、订单中心聚合）经本客户端消费付费能力；
 * 服务不可用时降级返回 {@link ResultCode#SERVICE_DEGRADED}，不阻断主链路——与 ReviewClient / MemberClient 同一范式。
 */
@Component
public class PaidChapterClient {

    private final PaidChapterService paidChapterService;

    public PaidChapterClient(PaidChapterService paidChapterService) {
        this.paidChapterService = paidChapterService;
    }

    /** 解锁单章；不可用时降级 SERVICE_DEGRADED */
    public R<ChapterEntitlementEntity> unlockChapter(Long userId, Long chapterId) {
        try {
            return paidChapterService.unlockChapter(userId, chapterId);
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 整本订阅；不可用时降级 SERVICE_DEGRADED */
    public R<BookSubscriptionEntity> subscribeBook(Long userId, Long bookId, BigDecimal amount) {
        try {
            return paidChapterService.subscribeBook(userId, bookId, amount);
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}
