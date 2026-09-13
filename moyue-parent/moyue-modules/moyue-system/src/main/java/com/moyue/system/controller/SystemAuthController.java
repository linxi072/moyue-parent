package com.moyue.system.controller;

import com.moyue.common.R;
import com.moyue.system.entity.SysLogininforEntity;
import com.moyue.system.service.LogininforService;
import com.moyue.system.service.OnlineUserService;
import com.moyue.system.service.SystemService;
import com.moyue.system.vo.AdminLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 运营后台登录接口（白名单路径，免网关鉴权）。
 * 完整前缀 /api/v1/system，登录成功后签发 role=3 令牌，供 /api/v1/admin/** 调用。
 * <p>登录埋点：成功/失败均异步写 sys_logininfor；成功额外写 Redis 在线会话
 * {@code moyue:online:{md5(token)}}。埋点全量 try-catch，绝不阻断登录主流程。</p>
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemAuthController {

    private static final Logger log = LoggerFactory.getLogger(SystemAuthController.class);

    @Autowired
    private SystemService systemService;

    @Autowired
    private LogininforService logininforService;

    @Autowired
    private OnlineUserService onlineUserService;

    /** accessToken 有效期（毫秒），与 SystemService 签发侧同 key，作为在线会话 TTL */
    @Value("${moyue.jwt.access-ttl:7200000}")
    private long accessTtl;

    /** 后台登录：POST /api/v1/system/login */
    @PostMapping("/login")
    public R<AdminLoginVO> login(@RequestBody LoginReq req, HttpServletRequest request) {
        try {
            AdminLoginVO vo = systemService.login(req.getUsername(), req.getPassword());
            // 埋点：登录成功 → 登录日志 + Redis 在线会话（失败只 warn 不阻断）
            recordLoginLog(req.getUsername(), request, 0, "登录成功");
            try {
                onlineUserService.addSession(vo.getAccessToken(), vo.getUserId(), vo.getNickname(),
                        resolveIp(request), request.getHeader("User-Agent"), accessTtl);
            } catch (Exception e) {
                log.warn("[login] 写在线会话失败（不影响登录）：{}", e.getMessage());
            }
            return R.ok(vo);
        } catch (Exception e) {
            // 埋点：登录失败 → 失败登录日志（保留原异常语义）
            recordLoginLog(req.getUsername(), request, 1,
                    e.getMessage() == null ? "登录失败" : e.getMessage());
            throw e;
        }
    }

    /** 异步写登录日志（任何异常吞掉，不影响登录） */
    private void recordLoginLog(String username, HttpServletRequest request, int status, String msg) {
        try {
            SysLogininforEntity entity = new SysLogininforEntity();
            entity.setUsername(username);
            entity.setIp(resolveIp(request));
            entity.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
            entity.setStatus(status);
            entity.setMsg(truncate(msg, 255));
            entity.setLoginTime(LocalDateTime.now());
            logininforService.record(entity);
        } catch (Exception e) {
            log.warn("[login] 记录登录日志失败：{}", e.getMessage());
        }
    }

    /** 取客户端真实 IP（优先 X-Forwarded-For / X-Real-IP） */
    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    /** 登录请求体 */
    @Data
    public static class LoginReq {
        private String username;
        private String password;
    }
}
