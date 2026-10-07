package com.moyue.author.center;

import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.security.SecurityContextHolder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作者创作中心 REST 入口（P0）。路径前缀 /api/v1/author/center 与网关、Feign 对齐。
 *
 * <p>所有端点首行统一身份校验：匿名（无 userId）→ UNAUTHORIZED(10002)；
 * 角色存在且 {@code <2}（读者等）→ FORBIDDEN(10003)。角色为 null 时仅校验匿名（与网关注入约定一致）。</p>
 *
 * <p>批量端点捕获 {@link BatchOperationException} → 经 {@link #batchFail} 封装为 {@code R<BatchResult>}(success=false, failedIds=列表) 回滚提示，HTTP 200 承载业务错误。</p>
 */
@RestController
@RequestMapping("/api/v1/author/center")
public class AuthorCenterController {

    @Autowired
    private AuthorCenterService authorCenterService;

    /** 汇总看板（@Cacheable 5min） */
    @GetMapping("/overview")
    public R<AuthorDashboardVO> overview() {
        Long userId = requireUserId();
        assertAuthor(currentRole());
        return R.ok(authorCenterService.authorOverview(userId));
    }

    /** 单作品看板（@Cacheable 5min，归属校验在 Service 内） */
    @GetMapping("/books/{bookId}/dashboard")
    public R<AuthorBookDashboardVO> bookDashboard(@PathVariable Long bookId) {
        Long userId = requireUserId();
        assertAuthor(currentRole());
        return R.ok(authorCenterService.bookDashboard(userId, bookId));
    }

    /** 稿酬流水（可选按书过滤，不分页） */
    @GetMapping("/income")
    public R<List<AuthorIncomeVO>> listIncome(@RequestParam(required = false) Long bookId) {
        Long userId = requireUserId();
        assertAuthor(currentRole());
        return R.ok(authorCenterService.listIncome(userId, bookId));
    }

    /** 批量删除章节（整批回滚 + 失败列表） */
    @PostMapping("/chapters/batch-delete")
    public R<BatchResult> batchDelete(@RequestBody BatchDeleteRequest req) {
        Long userId = requireUserId();
        Integer role = currentRole();
        assertAuthor(role);
        try {
            return R.ok(authorCenterService.batchDeleteChapters(userId, role, req.getChapterIds()));
        } catch (BatchOperationException ex) {
            return batchFail(ResultCode.CONTENT_BLOCKED, "批量操作失败，已回滚", ex.getFailedIds());
        }
    }

    /** 批量发布 / 定时发布（机审中断整批回滚 + 失败列表） */
    @PostMapping("/chapters/batch-publish")
    public R<BatchResult> batchPublish(@RequestBody BatchPublishRequest req) {
        Long userId = requireUserId();
        Integer role = currentRole();
        assertAuthor(role);
        try {
            return R.ok(authorCenterService.batchPublishChapters(userId, role, req.getChapterIds(), req.getPublishTime()));
        } catch (BatchOperationException ex) {
            return batchFail(ResultCode.CONTENT_BLOCKED, "批量操作失败，已回滚", ex.getFailedIds());
        }
    }

    /** 批量改状态（仅 0/1，整批回滚 + 失败列表） */
    @PostMapping("/chapters/batch-status")
    public R<BatchResult> batchStatus(@RequestBody BatchStatusRequest req) {
        Long userId = requireUserId();
        Integer role = currentRole();
        assertAuthor(role);
        try {
            return R.ok(authorCenterService.batchUpdateStatus(userId, role, req.getChapterIds(), req.getTargetStatus()));
        } catch (BatchOperationException ex) {
            return batchFail(ResultCode.CONTENT_BLOCKED, "批量操作失败，已回滚", ex.getFailedIds());
        }
    }

    // ------------------------------ 上下文工具 ------------------------------

    private Long requireUserId() {
        Long uid = SecurityContextHolder.currentUserId();
        if (uid == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return uid;
    }

    private Integer currentRole() {
        return SecurityContextHolder.currentRole();
    }

    /** 角色存在且 <2（读者等）则无权限；角色为 null 时仅校验匿名（放行） */
    private void assertAuthor(Integer role) {
        if (role != null && role < 2) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    /** 批量失败统一封装：success=false + failedIds，保持 {@code R<BatchResult>} 泛型一致 */
    private R<BatchResult> batchFail(ResultCode code, String msg, List<Long> failedIds) {
        R<BatchResult> r = R.fail(code, msg);
        BatchResult br = new BatchResult();
        br.setSuccess(false);
        br.setFailedIds(failedIds);
        r.setData(br);
        return r;
    }

    // ------------------------------ 请求体 ------------------------------

    /** 批量删除请求 */
    @Data
    public static class BatchDeleteRequest {
        private Long bookId;
        private List<Long> chapterIds;
    }

    /** 批量发布请求（publishTime 为空表示立即发布） */
    @Data
    public static class BatchPublishRequest {
        private Long bookId;
        private List<Long> chapterIds;
        private LocalDateTime publishTime;
    }

    /** 批量改状态请求（targetStatus 仅允许 0/1） */
    @Data
    public static class BatchStatusRequest {
        private Long bookId;
        private List<Long> chapterIds;
        private int targetStatus;
    }
}
