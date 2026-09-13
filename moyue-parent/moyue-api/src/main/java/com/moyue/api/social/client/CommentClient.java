package com.moyue.api.social.client;

import com.moyue.api.social.dto.CommentDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 评论服务 Feign 客户端（moyue-social）。
 * 返回类型包裹 R&lt;T&gt;，与控制器 {@code R<PageResult<CommentDTO>>} 结构一致。
 * CommentController 已在 CommentService.toDtoPage 做实体转换后返回 CommentDTO，与此处声明严格对齐。
 */
@FeignClient(name = "moyue-social", fallbackFactory = CommentClientFallbackFactory.class)
public interface CommentClient {

    /** 按书籍分页查询评论 */
    @GetMapping("/api/v1/comments")
    R<PageResult<CommentDTO>> listComments(@RequestParam("bookId") Long bookId,
                                           @RequestParam("page") int page,
                                           @RequestParam("size") int size);

    /**
     * 审核回写评论状态（内部端点，不经网关）：1=已通过 / 2=已驳回。
     * 供 moyue-platform 审核裁决后调用。
     */
    @PutMapping("/api/v1/internal/comments/{commentId}/audit")
    R<Void> auditComment(@PathVariable("commentId") Long commentId,
                         @RequestParam("status") Integer status);
}
