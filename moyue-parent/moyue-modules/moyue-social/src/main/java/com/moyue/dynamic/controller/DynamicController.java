package com.moyue.dynamic.controller;

import com.moyue.api.social.dto.DynamicDTO;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.CursorPageResult;
import com.moyue.dynamic.service.DynamicService;
import com.moyue.follow.service.FollowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动态接口（P2-E，经网关）：粉丝时间线 feed / 作者主页动态页。
 *
 * <p>feed 的 userId 取网关注入头 X-User-Id（绝不取请求体）；作者主页动态页为公开接口，authorId 取自路径。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class DynamicController {

    @Autowired
    private DynamicService dynamicService;

    @Autowired
    private FollowService followService;

    /** 粉丝时间线 feed：GET /api/v1/feed?cursor=&size=（fan-out-on-read，关注即见、取关即失） */
    @GetMapping("/feed")
    public R<CursorPageResult<DynamicDTO>> feed(@RequestParam(required = false) String cursor,
                                               @RequestParam(defaultValue = "20") int size,
                                               HttpServletRequest request) {
        long userId = requireUserId(request);
        return R.ok(dynamicService.timeline(followService.findFollowedAuthorIds(userId), cursor, size));
    }

    /** 作者主页动态页：GET /api/v1/dynamic/author/{authorId}?cursor=&size= */
    @GetMapping("/dynamic/author/{authorId}")
    public R<CursorPageResult<DynamicDTO>> authorDynamics(@PathVariable Long authorId,
                                                         @RequestParam(required = false) String cursor,
                                                         @RequestParam(defaultValue = "20") int size) {
        return R.ok(dynamicService.pageByAuthor(authorId, cursor, size));
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
