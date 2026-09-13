package com.moyue.system.controller;

import com.moyue.common.R;
import com.moyue.system.service.SystemService;
import com.moyue.system.vo.AdminLoginVO;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营后台登录接口（白名单路径，免网关鉴权）。
 * 完整前缀 /api/v1/system，登录成功后签发 role=3 令牌，供 /api/v1/admin/** 调用。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemAuthController {

    @Autowired
    private SystemService systemService;

    /** 后台登录：POST /api/v1/system/login */
    @PostMapping("/login")
    public R<AdminLoginVO> login(@RequestBody LoginReq req) {
        return R.ok(systemService.login(req.getUsername(), req.getPassword()));
    }

    /** 登录请求体 */
    @Data
    public static class LoginReq {
        private String username;
        private String password;
    }
}
