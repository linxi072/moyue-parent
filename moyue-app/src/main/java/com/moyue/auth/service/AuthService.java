package com.moyue.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.auth.dto.LoginReq;
import com.moyue.auth.dto.RefreshReq;
import com.moyue.auth.dto.RegisterReq;
import com.moyue.auth.entity.AuthUserEntity;
import com.moyue.auth.mapper.AuthUserMapper;
import com.moyue.auth.vo.LoginVO;
import com.moyue.auth.vo.UserInfoVO;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.JwtProvider;
import com.moyue.common.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.moyue.api.risk.client.BehaviorRiskClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 鉴权业务（基于 MySQL user 表 + Redis refreshToken，替换原内存演示实现）。
 * 演示用户由 {@code CommandLineRunner} 以 BCrypt 写入，id 固定为 1 以对齐 V2 种子引用。
 */
@Service
public class AuthService implements CommandLineRunner {

    /** refreshToken 在 Redis 中的 key 前缀 */
    private static final String REFRESH_KEY_PREFIX = "moyue:refresh:";

    @Autowired
    private AuthUserMapper userMapper;

    @Autowired
    private JwtProvider jwtProvider;

    /** 行为风控客户端（P2-C 闭环补全）：登录成功后非阻断埋点 */
    @Autowired(required = false)
    private BehaviorRiskClient behaviorRiskClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${moyue.jwt.access-ttl:7200000}")
    private long accessTtl;

    @Value("${moyue.jwt.refresh-ttl:604800000}")
    private long refreshTtl;

    @Value("${moyue.demo.phone:13800000000}")
    private String demoPhone;

    @Value("${moyue.demo.password:123456}")
    private String demoPassword;

    @Value("${moyue.demo.nickname:墨阅读者}")
    private String demoNickname;

    @Value("${moyue.demo.role:1}")
    private Integer demoRole;

    /** 启动时确保演示用户存在（BCrypt 写入，id 固定为 1） */
    @Override
    public void run(String... args) {
        AuthUserEntity exist = userMapper.selectOne(
                new LambdaQueryWrapper<AuthUserEntity>().eq(AuthUserEntity::getPhone, demoPhone));
        if (exist == null) {
            AuthUserEntity user = new AuthUserEntity();
            user.setId(1L);
            user.setPhone(demoPhone);
            user.setNickname(demoNickname);
            user.setPassword(encoder.encode(demoPassword));
            user.setRole(demoRole);
            user.setStatus(1);
            userMapper.insert(user);
        }
    }

    /** 登录：校验手机号 + BCrypt 密码，签发双令牌 */
    public LoginVO login(LoginReq req) {
        AuthUserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<AuthUserEntity>().eq(AuthUserEntity::getPhone, req.getPhone()));
        if (user == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "手机号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已禁用");
        }
        LoginVO vo = issueTokens(user);
        collectLoginRisk(user.getId());
        return vo;
    }

    /**
     * 登录行为风控埋点（P2-C 闭环补全）：非阻断采集，deviceId / IP 最佳努力从请求头与上下文获取。
     * 风控服务未就绪 / 无 Web 上下文 / 异常时仅告警吞掉，绝不回滚、不阻断登录主链路。
     */
    private void collectLoginRisk(Long userId) {
        if (behaviorRiskClient == null) {
            return;
        }
        try {
            String deviceId = null;
            String ip = null;
            try {
                RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
                if (attrs instanceof ServletRequestAttributes sra) {
                    HttpServletRequest req = sra.getRequest();
                    deviceId = req.getHeader("X-Device-Id");
                    ip = req.getRemoteAddr();
                }
            } catch (Exception ignored) {
                // 非 Web 上下文（如单元测试）忽略
            }
            behaviorRiskClient.collect(userId, deviceId, "LOGIN", null, ip);
        } catch (Exception ignored) {
            // collect 本身已降级；双保险
        }
    }

    /** 注册：写入 user 表（BCrypt） */
    public void register(RegisterReq req) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<AuthUserEntity>().eq(AuthUserEntity::getPhone, req.getPhone()));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "该手机号已注册");
        }
        AuthUserEntity user = new AuthUserEntity();
        user.setPhone(req.getPhone());
        user.setNickname("墨阅用户" + req.getPhone().substring(7));
        user.setPassword(encoder.encode(req.getPassword()));
        user.setRole(1);
        user.setStatus(1);
        userMapper.insert(user);
    }

    /** 刷新：校验 refreshToken 合法性及 Redis 中是否仍有效，重新签发 accessToken */
    public LoginVO refresh(RefreshReq req) {
        JwtProvider.JwtClaims claims = jwtProvider.parse(req.getRefreshToken());
        if (!Constants.TOKEN_TYPE_REFRESH.equals(claims.getType())) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        String key = REFRESH_KEY_PREFIX + req.getRefreshToken();
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            throw new BizException(ResultCode.TOKEN_EXPIRED, "refreshToken 已失效，请重新登录");
        }
        AuthUserEntity user = userMapper.selectById(claims.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        return issueTokens(user);
    }

    /** 获取当前登录用户资料（userId 由网关注入） */
    public UserInfoVO me(Long userId) {
        AuthUserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        return vo;
    }

    /** 签发双令牌：refreshToken 写入 Redis（带 TTL），accessToken 无状态 */
    private LoginVO issueTokens(AuthUserEntity user) {
        String access = jwtProvider.createAccessToken(user.getId(), user.getRole(), accessTtl);
        String refresh = jwtProvider.createRefreshToken(user.getId(), refreshTtl);

        redisTemplate.opsForValue().set(REFRESH_KEY_PREFIX + refresh, String.valueOf(user.getId()),
                refreshTtl, TimeUnit.MILLISECONDS);

        LoginVO vo = new LoginVO();
        vo.setAccessToken(access);
        vo.setRefreshToken(refresh);
        return vo;
    }
}
