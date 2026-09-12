package com.moyue.comment.controller;

import com.moyue.comment.service.CommentService;
import com.moyue.common.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论内部端点（服务间调用，仅供 Feign 使用）。
 * 路径 /api/v1/internal/** 不在网关任何路由内，外部不可达。
 */
@RestController
@RequestMapping("/api/v1/internal")
public class CommentInternalController {

    @Autowired
    private CommentService commentService;

    /** 审核回写：status 1=已通过 / 2=已驳回 */
    @PutMapping("/comments/{commentId}/audit")
    public R<Void> audit(@PathVariable Long commentId, @RequestParam Integer status) {
        commentService.auditComment(commentId, status);
        return R.ok();
    }
}
