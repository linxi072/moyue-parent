package com.moyue.follow.controller;

import com.moyue.api.social.dto.FollowDTO;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.CursorPageResult;
import com.moyue.follow.service.FollowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关注关系接口（P2-E，经网关）：关注 / 取关 / 关注列表 / 粉丝列表。
 *
 * <p>userId 一律取网关注入头 X-User-Id，绝不信任请求体（防越权，参照 CommentController.requireUserId）。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class FollowController {

    @Autowired
    private FollowService followService;

    /** 关注作者：POST /api/v1/follow?authorId= */
    @PostMapping("/follow")
    public R<FollowDTO> follow(@RequestParam Long authorId, HttpServletRequest request) {
        long userId = requireUserId(request);
        return R.ok(followService.follow(userId, authorId));
    }

    /** 取关作者：DELETE /api/v1/follow/{authorId}（逻辑删） */
    @DeleteMapping("/follow/{authorId}")
    public R<Void> unfollow(@PathVariable Long authorId, HttpServletRequest request) {
        long userId = requireUserId(request);
        followService.unfollow(userId, authorId);
        return R.ok();
    }

    /** 我关注的人：GET /api/v1/follow/following?cursor=&size= */
    @GetMapping("/follow/following")
    public R<CursorPageResult<FollowDTO>> following(@RequestParam(required = false) String cursor,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   HttpServletRequest request) {
        long userId = requireUserId(request);
        return R.ok(followService.listFollowing(userId, cursor, size));
    }

    /** 我的粉丝：GET /api/v1/follow/followers?cursor=&size= */
    @GetMapping("/follow/followers")
    public R<CursorPageResult<FollowDTO>> followers(@RequestParam(required = false) String cursor,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   HttpServletRequest request) {
        long userId = requireUserId(request);
        return R.ok(followService.listFollowers(userId, cursor, size));
    }

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
}
