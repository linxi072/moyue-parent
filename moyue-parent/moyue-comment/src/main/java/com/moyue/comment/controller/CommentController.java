package com.moyue.comment.controller;

import com.moyue.api.dto.PageResult;
import com.moyue.comment.entity.CommentEntity;
import com.moyue.comment.service.CommentService;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论接口：查询 / 发表 / 删除 / 点赞。
 * 路径前缀 /api/v1 与网关路由、Feign CommentClient 保持一致。
 * 评论人一律取网关注入的 X-User-Id，不接受请求体传入 userId（防越权）。
 */
@RestController
@RequestMapping("/api/v1")
public class CommentController {

    @Autowired
    private CommentService commentService;

    /** 按书籍分页查询评论：GET /api/v1/comments?bookId=&page=&size= */
    @GetMapping("/comments")
    public R<PageResult<CommentEntity>> list(@RequestParam Long bookId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return R.ok(commentService.listByBook(bookId, page, size));
    }

    /** 发表评论：POST /api/v1/comments（评论人取网关注入头） */
    @PostMapping("/comments")
    public R<CommentEntity> add(@RequestBody CreateCommentRequest req, HttpServletRequest request) {
        long userId = requireUserId(request);
        return R.ok(commentService.addComment(userId, req.getBookId(), req.getChapterId(), req.getContent()));
    }

    /** 删除评论（本人 / 管理员） */
    @DeleteMapping("/comments/{commentId}")
    public R<Void> delete(@PathVariable Long commentId, HttpServletRequest request) {
        long userId = requireUserId(request);
        int role = currentRole(request);
        commentService.deleteComment(commentId, userId, role);
        return R.ok();
    }

    /** 点赞切换：POST /api/v1/comments/{commentId}/like（返回当前 like_count） */
    @PostMapping("/comments/{commentId}/like")
    public R<Integer> toggleLike(@PathVariable Long commentId, HttpServletRequest request) {
        long userId = requireUserId(request);
        return R.ok(commentService.toggleLike(commentId, userId));
    }

    // ------------------------------ 上下文工具 ------------------------------

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

    private int currentRole(HttpServletRequest request) {
        String role = request.getHeader(Constants.USER_ROLE_HEADER);
        if (role == null || role.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(role.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // ------------------------------ 请求体 ------------------------------

    /** 发表评论请求 */
    @Data
    public static class CreateCommentRequest {
        private Long bookId;
        private Long chapterId;
        private String content;
    }
}
