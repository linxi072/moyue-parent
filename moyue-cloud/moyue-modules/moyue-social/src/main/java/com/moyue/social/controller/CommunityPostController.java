package com.moyue.social.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.social.domain.dto.query.CommunityPostQuery;
import com.moyue.social.domain.entity.CommunityPost;
import com.moyue.social.domain.vo.CommunityPostVO;
import com.moyue.social.service.CommunityPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区帖子管理（社区域）：CRUD + 运营加热。
 *
 * @author moyue
 */
@Tag(name = "社区帖子", description = "社区动态增删改查与运营加热（点赞 +1）")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/social/posts")
@RequiredArgsConstructor
public class CommunityPostController {

    private final CommunityPostService postService;

    @Operation(summary = "帖子分页")
    @RequiresPermissions("social:post:list")
    @GetMapping
    public R<PageResult<CommunityPostVO>> page(CommunityPostQuery query) {
        return R.ok(postService.pagePosts(query));
    }

    @Operation(summary = "新建帖子")
    @RequiresPermissions("social:post:add")
    @Log(title = "社区帖子", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody CommunityPost entity) {
        return R.ok(postService.createPost(entity));
    }

    @Operation(summary = "编辑帖子")
    @RequiresPermissions("social:post:edit")
    @Log(title = "社区帖子", businessType = BusinessType.UPDATE)
    @PutMapping("/{postId}")
    public R<Boolean> update(@PathVariable Long postId, @RequestBody CommunityPost entity) {
        entity.setId(postId);
        return R.ok(postService.updatePost(entity));
    }

    @Operation(summary = "删除帖子")
    @RequiresPermissions("social:post:remove")
    @Log(title = "社区帖子", businessType = BusinessType.DELETE)
    @DeleteMapping("/{postId}")
    public R<Boolean> delete(@PathVariable Long postId) {
        return R.ok(postService.deletePost(postId));
    }

    @Operation(summary = "运营加热（点赞 +1）")
    @RequiresPermissions("social:post:edit")
    @Log(title = "社区帖子", businessType = BusinessType.UPDATE)
    @PostMapping("/{postId}/like")
    public R<Boolean> like(@PathVariable Long postId) {
        return R.ok(postService.like(postId));
    }
}
