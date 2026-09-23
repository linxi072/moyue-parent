package com.moyue.api.social.client;

import com.moyue.api.social.dto.CommentDTO;
import com.moyue.comment.service.CommentService;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import org.springframework.stereotype.Component;

/**
 * 评论服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-social) 已移除 OpenFeign，改为直接注入 {@link CommentService} 委托调用。
 */
@Component
public class CommentClient {

    private final CommentService commentService;

    public CommentClient(CommentService commentService) {
        this.commentService = commentService;
    }

    /** 按书籍分页查询评论 */
    public R<PageResult<CommentDTO>> listComments(Long bookId, int page, int size) {
        try {
            return R.ok(CommentService.toDtoPage(commentService.listByBook(bookId, page, size)));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 审核回写评论状态（1=已通过 / 2=已驳回） */
    public R<Void> auditComment(Long commentId, Integer status) {
        try {
            commentService.auditComment(commentId, status);
            return R.ok();
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 按 ID 查询单条评论（内部端点用，评论不存在返回 data=null） */
    public R<CommentDTO> getComment(Long commentId) {
        try {
            return R.ok(CommentService.toDto(commentService.getById(commentId)));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}
