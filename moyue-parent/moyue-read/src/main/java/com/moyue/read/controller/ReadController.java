package com.moyue.read.controller;

import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.read.entity.BookshelfEntity;
import com.moyue.read.service.ReadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阅读接口：书架查询与增删、阅读进度。
 * 路径前缀 /api/v1 与网关路由保持一致。
 * 写接口操作的书架一律归属网关注入的 X-User-Id，不接受前端传入 userId。
 */
@RestController
@RequestMapping("/api/v1")
public class ReadController {

    @Autowired
    private ReadService readService;

    /** 按用户 ID 获取书架 */
    @GetMapping("/read/bookshelf/{userId}")
    public R<List<BookshelfEntity>> getBookshelf(@PathVariable Long userId) {
        return R.ok(readService.getShelf(userId));
    }

    /** 加入书架（幂等） */
    @PostMapping("/read/bookshelf")
    public R<Void> addToShelf(@RequestBody AddShelfRequest req, HttpServletRequest request) {
        readService.addToShelf(requireUserId(request), req.getBookId());
        return R.ok();
    }

    /** 移出书架 */
    @DeleteMapping("/read/bookshelf/{bookId}")
    public R<Void> removeFromShelf(@PathVariable Long bookId, HttpServletRequest request) {
        readService.removeFromShelf(requireUserId(request), bookId);
        return R.ok();
    }

    /** 更新阅读进度（最后阅读章节） */
    @PutMapping("/read/bookshelf/{bookId}/progress")
    public R<Void> updateProgress(@PathVariable Long bookId,
                                  @RequestBody ProgressRequest req,
                                  HttpServletRequest request) {
        readService.updateProgress(requireUserId(request), bookId, req.getChapterId());
        return R.ok();
    }

    // ------------------------------ 上下文工具 ------------------------------

    private Long requireUserId(HttpServletRequest request) {
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

    /** 加入书架请求 */
    @Data
    public static class AddShelfRequest {
        private Long bookId;
    }

    /** 更新进度请求 */
    @Data
    public static class ProgressRequest {
        private Long chapterId;
    }
}
