package com.moyue.social.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论管理（互动域·运营端）：列表 / 删除 / 置顶 / 审核。
 *
 * @author moyue
 */
@Tag(name = "评论管理", description = "评论列表 / 删除 / 置顶 / 审核")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/social/comments")
@RequiredArgsConstructor
public class CommentAdminController {

    private final CommentService commentService;

    @Operation(summary = "评论分页（运营）")
    @RequiresPermissions("social:comment:list")
    @GetMapping
    public R<PageResult<CommentVO>> page(CommentQuery query) {
        return R.ok(commentService.pageAdminComments(query));
    }

    @Operation(summary = "删除评论", description = "逻辑删除，无视作者身份")
    @RequiresPermissions("social:comment:remove")
    @Log(title = "评论管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(commentService.adminDelete(id));
    }

    @Operation(summary = "置顶 / 取消置顶", description = "top=1 置顶 / 0 取消")
    @RequiresPermissions("social:comment:edit")
    @Log(title = "评论管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/top")
    public R<Boolean> top(@PathVariable Long id, @RequestParam int top) {
        return R.ok(commentService.top(id, top));
    }

    @Operation(summary = "审核评论", description = "status 0 正常 / 1 待审核 / 2 已下架")
    @RequiresPermissions("social:comment:audit")
    @Log(title = "评论管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/audit")
    public R<Boolean> audit(@PathVariable Long id, @RequestParam Integer status) {
        return R.ok(commentService.audit(id, status));
    }
}
