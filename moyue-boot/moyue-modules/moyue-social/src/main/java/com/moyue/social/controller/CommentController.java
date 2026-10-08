package com.moyue.social.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.R;
import com.moyue.common.security.context.UserContext;
import com.moyue.social.domain.dto.query.CommentQuery;
import com.moyue.social.domain.vo.CommentVO;
import com.moyue.social.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论（C 端互动）。
 *
 * @author moyue
 */
@Tag(name = "评论", description = "评论查询/发表/删除/点赞切换")
@Validated
@RestController
@RequestMapping(Constants.API_PREFIX + "/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "评论分页（按 book_id 聚合）")
    @GetMapping
    public R<?> page(CommentQuery query) {
        return R.ok(commentService.pageComments(query));
    }

    @Operation(summary = "发表评论")
    @PostMapping
    public R<Long> create(@RequestBody CreateCommentBody body) {
        return R.ok(commentService.createComment(currentUser(), body.bookId(),
                body.chapterId(), body.replyTo(), body.content()));
    }

    @Operation(summary = "删除评论（仅本人）")
    @DeleteMapping("/{commentId}")
    public R<Boolean> delete(@PathVariable Long commentId) {
        commentService.deleteComment(currentUser(), commentId);
        return R.ok(true);
    }

    @Operation(summary = "点赞切换（唯一键防重，返回当前点赞数）")
    @PostMapping("/{commentId}/like")
    public R<Integer> like(@PathVariable Long commentId) {
        return R.ok(commentService.toggleLike(currentUser(), commentId));
    }

    private Long currentUser() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }

    /** 发表评论入参 */
    public record CreateCommentBody(Long bookId, Long chapterId, Long replyTo, String content) {
    }
}
