package com.moyue.system.controller;

import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.security.RequiresPermissions;
import com.moyue.system.annotation.Log;
import com.moyue.system.service.OnlineUserService;
import com.moyue.system.vo.OnlineUserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线用户接口。
 * 完整前缀 /api/v1/admin/system/online，强退需 system:online:forceLogout 权限码并落操作日志。
 */
@RestController
@RequestMapping("/api/v1/admin/system/online")
public class SysOnlineController {

    @Autowired
    private OnlineUserService onlineUserService;

    /** 在线用户分页（可选 keyword 匹配昵称/IP）：GET /api/v1/admin/system/online/list */
    @GetMapping("/list")
    public R<PageResult<OnlineUserVO>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String keyword) {
        return R.ok(onlineUserService.page(page, size, keyword));
    }

    /** 强退（删会话 + 写黑名单，令牌立即失效）：DELETE /api/v1/admin/system/online/{tokenId} */
    @DeleteMapping("/{tokenId}")
    @RequiresPermissions("system:online:forceLogout")
    @Log(module = "在线用户", businessType = Log.BusinessType.FORCE)
    public R<Void> forceLogout(@PathVariable String tokenId) {
        onlineUserService.forceLogout(tokenId);
        return R.ok();
    }
}
