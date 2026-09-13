package com.moyue.book.controller;

import com.moyue.api.dto.PageResult;
import com.moyue.book.entity.BookFinishApplyEntity;
import com.moyue.book.service.BookFinishApplyService;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作品完结申请审核流接口（V11 book_finish_apply）。
 * <p>作者侧：提交申请 / 查询本人作品最新申请；
 * 管理侧（{@code /api/v1/admin/**}，AdminRoleInterceptor 已断言 role=3）：分页查询与裁决。</p>
 * 用户身份一律取网关注入的 X-User-Id，杜绝前端伪造。
 */
@RestController
@RequestMapping("/api/v1")
public class BookFinishApplyController {

    @Autowired
    private BookFinishApplyService finishApplyService;

    /** 提交完结申请（作者本人） */
    @PostMapping("/books/{bookId}/finish-apply")
    public R<BookFinishApplyEntity> apply(@PathVariable Long bookId,
                                          @RequestBody(required = false) ApplyRequest req,
                                          HttpServletRequest request) {
        long userId = requireUserId(request);
        String reason = req == null ? null : req.getReason();
        return R.ok(finishApplyService.apply(bookId, userId, reason));
    }

    /** 查询本人作品最新一条完结申请（无申请 → 20001） */
    @GetMapping("/books/{bookId}/finish-apply")
    public R<BookFinishApplyEntity> latest(@PathVariable Long bookId, HttpServletRequest request) {
        long userId = requireUserId(request);
        BookFinishApplyEntity apply = finishApplyService.latestByBook(bookId);
        if (apply == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (apply.getAuthorId() == null || apply.getAuthorId() != userId) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return R.ok(apply);
    }

    /** 后台：完结申请分页（status 为空不过滤） */
    @GetMapping("/admin/books/finish-applies")
    public R<PageResult<BookFinishApplyEntity>> pageApplies(@RequestParam(required = false) Integer status,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return R.ok(finishApplyService.pageApplies(status, page, size));
    }

    /** 后台：通过完结申请（通过即把作品置为已完结） */
    @PutMapping("/admin/books/finish-applies/{id}/approve")
    public R<BookFinishApplyEntity> approve(@PathVariable Long id,
                                            @RequestParam(required = false) String remark,
                                            HttpServletRequest request) {
        long auditorId = requireUserId(request);
        return R.ok(finishApplyService.decide(id, true, remark, auditorId));
    }

    /** 后台：驳回完结申请 */
    @PutMapping("/admin/books/finish-applies/{id}/reject")
    public R<BookFinishApplyEntity> reject(@PathVariable Long id,
                                           @RequestParam(required = false) String remark,
                                           HttpServletRequest request) {
        long auditorId = requireUserId(request);
        return R.ok(finishApplyService.decide(id, false, remark, auditorId));
    }

    // ------------------------------ 上下文工具 ------------------------------

    /** 从网关注入头取当前用户 ID；缺失 / 非法 → 10002 未登录 */
    private long requireUserId(HttpServletRequest request) {
        String uid = request.getHeader(Constants.USER_ID_HEADER);
        if (uid == null || uid.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(uid.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }

    // ------------------------------ 请求体 ------------------------------

    /** 完结申请请求（reason 可空） */
    @Data
    public static class ApplyRequest {
        private String reason;
    }
}
