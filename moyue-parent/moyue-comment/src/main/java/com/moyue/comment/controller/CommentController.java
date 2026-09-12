package com.moyue.comment.controller;

import com.moyue.api.dto.PageResult;
import com.moyue.comment.entity.CommentEntity;
import com.moyue.comment.service.CommentService;
import com.moyue.common.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论接口。
 * 路径前缀 /api/v1 与网关路由、Feign CommentClient 保持一致。
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

    /** 发表评论：POST /api/v1/comments */
    @PostMapping("/comments")
    public R<Void> add(@RequestBody CommentEntity entity) {
        commentService.add(entity);
        return R.ok();
    }
}
