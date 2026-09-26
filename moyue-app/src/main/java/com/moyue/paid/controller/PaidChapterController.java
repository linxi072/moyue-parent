package com.moyue.paid.controller;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.security.SecurityContextHolder;
import com.moyue.paid.entity.BookSubscriptionEntity;
import com.moyue.paid.entity.ChapterEntitlementEntity;
import com.moyue.paid.service.PaidChapterService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 付费章节 / 订阅阅读接口。
 * <p>身份取 {@link SecurityContextHolder}（网关注入的 X-User-Id / X-User-Role），匿名请求拒绝。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class PaidChapterController {

    @Autowired
    private PaidChapterService paidChapterService;

    /** 解锁（购买）单章：POST /api/v1/chapters/{chapterId}/unlock */
    @PostMapping("/chapters/{chapterId}/unlock")
    public R<ChapterEntitlementEntity> unlockChapter(@PathVariable Long chapterId) {
        Long userId = SecurityContextHolder.currentUserId();
        if (userId == null) {
            return R.fail(ResultCode.UNAUTHORIZED);
        }
        return paidChapterService.unlockChapter(userId, chapterId);
    }

    /** 整本订阅：POST /api/v1/books/{bookId}/subscribe（amount 可选） */
    @PostMapping("/books/{bookId}/subscribe")
    public R<BookSubscriptionEntity> subscribeBook(@PathVariable Long bookId,
                                                   @RequestBody(required = false) SubscribeRequest req) {
        Long userId = SecurityContextHolder.currentUserId();
        if (userId == null) {
            return R.fail(ResultCode.UNAUTHORIZED);
        }
        BigDecimal amount = req != null ? req.getAmount() : null;
        return paidChapterService.subscribeBook(userId, bookId, amount);
    }

    /** 我的解锁清单：GET /api/v1/me/entitlements?bookId=（bookId 可选过滤） */
    @GetMapping("/me/entitlements")
    public R<List<ChapterEntitlementEntity>> myEntitlements(@RequestParam(required = false) Long bookId) {
        Long userId = SecurityContextHolder.currentUserId();
        if (userId == null) {
            return R.fail(ResultCode.UNAUTHORIZED);
        }
        List<ChapterEntitlementEntity> list = paidChapterService.listActiveEntitlements(userId);
        if (bookId != null) {
            list = list.stream()
                    .filter(e -> bookId.equals(e.getBookId()))
                    .collect(Collectors.toList());
        }
        return R.ok(list);
    }

    /** 整本订阅请求体（amount 可选，NULL 表示 0 元 stub 契约） */
    @Data
    public static class SubscribeRequest {
        private BigDecimal amount;
    }
}
