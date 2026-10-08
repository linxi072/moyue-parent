package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.system.domain.entity.SysUserOnline;
import com.moyue.system.service.SysOnlineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线用户（⑨）：5 个端点。Redis 主存 + sys_user_online 审计。
 *
 * @author moyue
 */
@Tag(name = "在线用户", description = "在线会话查询与强制下线")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/online")
@RequiredArgsConstructor
public class SysOnlineController {

    private final SysOnlineService onlineService;

    @Operation(summary = "在线用户列表")
    @RequiresPermissions("system:online:list")
    @GetMapping
    public R<PageResult<SysUserOnline>> page(@RequestParam(required = false) String username,
                                              @RequestParam(required = false) String ip,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return R.ok(onlineService.pageOnline(username, ip, page, size));
    }

    @Operation(summary = "会话详情")
    @RequiresPermissions("system:online:query")
    @GetMapping("/{tokenId}")
    public R<SysUserOnline> detail(@PathVariable String tokenId) {
        return R.ok(onlineService.detail(tokenId));
    }

    @Operation(summary = "强制下线", description = "删除 Redis 会话 + 标记下线")
    @RequiresPermissions("system:online:kick")
    @Log(title = "在线用户", businessType = BusinessType.FORCE)
    @DeleteMapping("/{tokenId}")
    public R<Boolean> kick(@PathVariable String tokenId) {
        return R.ok(onlineService.kick(tokenId));
    }

    @Operation(summary = "批量强制下线")
    @RequiresPermissions("system:online:kick")
    @Log(title = "在线用户", businessType = BusinessType.FORCE)
    @DeleteMapping
    public R<Integer> kickBatch(@RequestBody List<String> tokenIds) {
        return R.ok(onlineService.kickBatch(tokenIds));
    }

    @Operation(summary = "当前在线人数")
    @RequiresPermissions("system:online:list")
    @GetMapping("/count")
    public R<Long> count() {
        return R.ok(onlineService.count());
    }
}
