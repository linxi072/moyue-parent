package com.moyue.auth.service.impl;

import com.moyue.auth.domain.dto.LoginRequest;
import com.moyue.auth.domain.dto.RegisterRequest;
import com.moyue.auth.domain.entity.AuthUser;
import com.moyue.auth.domain.vo.TokenVO;
import com.moyue.auth.mapper.AuthUserMapper;
import com.moyue.auth.service.AuthService;
import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.log.model.LoginLogDTO;
import com.moyue.common.log.sink.LoginLogSink;
import com.moyue.common.log.util.IpUtils;
import com.moyue.common.redis.constant.CacheNames;
import com.moyue.common.redis.service.RedisService;
import com.moyue.common.security.model.LoginUser;
import com.moyue.common.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现。
 *
 * <p>登录链路：锁定校验 → 查用户 → BCrypt 比对 → 加载角色权限 → 签发令牌 → 记登录日志。
 * 失败时统一抛 {@code UNAUTHORIZED} 且<strong>不区分「账号不存在」与「密码错误」</strong>，
 * 避免账号枚举。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 连续失败锁定阈值（生产应取自 sys_config: sys.account.maxRetryCount） */
    private static final int DEFAULT_MAX_RETRY = 5;
    /** 锁定时长 */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final AuthUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedisService redisService;
    private final LoginLogSink loginLogSink;

    @Override
    public TokenVO login(LoginRequest request, String ip, String userAgent) {
        String account = request.getUsername();
        try {
            checkNotLocked(account);

            AuthUser user = userMapper.selectByAccount(account);
            if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                recordFail(account, ip, userAgent, "账号或密码错误");
                throw BusinessException.unauthorized();
            }
            if (Integer.valueOf(Constants.STATUS_DISABLE).equals(user.getStatus())) {
                recordFail(account, ip, userAgent, "账号已停用");
                throw new BusinessException(ErrorCode.FORBIDDEN, "账号已停用，请联系管理员");
            }

            LoginUser loginUser = loadLoginUser(user);
            clearFail(account);
            touchLogin(user, ip);
            recordSuccess(user, ip, userAgent);
            return buildToken(loginUser);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("登录异常", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "登录失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterRequest request) {
        int userType = request.getUserType() == null ? Constants.USER_TYPE_READER : request.getUserType();
        // 运营主体不允许自助注册：后台账号必须由管理员创建并显式授权
        if (userType != Constants.USER_TYPE_READER && userType != Constants.USER_TYPE_AUTHOR) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持注册读者 / 作者账号");
        }
        if (usernameTaken(request.getUsername())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账号已被占用：" + request.getUsername());
        }
        AuthUser user = new AuthUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank()
                ? request.getUsername() : request.getNickname());
        user.setPhone(request.getPhone());
        user.setUserType(userType);
        user.setStatus(Constants.STATUS_ENABLE);
        user.setIsDeleted(Constants.NOT_DELETED);
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    public boolean usernameTaken(String username) {
        return userMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<AuthUser>().eq(AuthUser::getUsername, username)) > 0;
    }

    @Override
    public TokenVO refresh(String refreshToken) {
        LoginUser user = jwtUtils.parse(refreshToken);
        if (user == null || user.getUserId() == null) {
            throw BusinessException.unauthorized();
        }
        AuthUser db = userMapper.selectById(user.getUserId());
        if (db == null || Integer.valueOf(Constants.DELETED).equals(db.getIsDeleted())) {
            throw BusinessException.unauthorized();
        }
        // 重新加载角色权限：避免沿用过期授权（用户可能已被改权限）
        return buildToken(loadLoginUser(db));
    }

    // ---------------------------------------------------------- 内部方法

    private LoginUser loadLoginUser(AuthUser user) {
        List<String> roleKeys = userMapper.selectRoleKeys(user.getId());
        Set<String> roles = roleKeys == null ? Set.of() : Set.copyOf(roleKeys);
        boolean superAdmin = roles.contains(Constants.SUPER_ROLE_KEY);
        Set<String> perms = superAdmin ? Set.of() : copyOf(userMapper.selectPerms(user.getId()));
        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .userType(user.getUserType())
                .deptId(user.getDeptId())
                .roleKeys(roles)
                .permissions(perms)
                .superAdmin(superAdmin)
                .build();
    }

    private TokenVO buildToken(LoginUser user) {
        return TokenVO.builder()
                .accessToken(jwtUtils.createAccessToken(user))
                .refreshToken(jwtUtils.createRefreshToken(user))
                .expiresIn(jwtUtils.accessExpireSeconds())
                .userId(user.getUserId())
                .nickname(user.getUsername())
                .userType(user.getUserType())
                .roles(user.getRoleKeys())
                .build();
    }

    private void touchLogin(AuthUser user, String ip) {
        AuthUser update = new AuthUser();
        update.setId(user.getId());
        update.setLoginIp(ip);
        update.setLoginDate(LocalDateTime.now());
        userMapper.updateById(update);
    }

    private void checkNotLocked(String account) {
        Integer failCount;
        try {
            failCount = redisService.get(lockKey(account), Integer.class);
        } catch (Exception e) {
            log.warn("登录失败计数读取失败：{}", e.getMessage());
            return;
        }
        if (failCount != null && failCount >= DEFAULT_MAX_RETRY) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "连续登录失败次数过多，账号已锁定 10 分钟");
        }
    }

    private void recordFail(String account, String ip, String userAgent, String message) {
        try {
            Long count = redisService.increment(lockKey(account), 1);
            if (count != null && count == 1L) {
                redisService.expire(lockKey(account), LOCK_DURATION.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("登录失败计数写入失败：{}", e.getMessage());
        }
        loginLogSink.save(LoginLogDTO.builder()
                .username(account)
                .ip(ip)
                .os(userAgent)
                .status(1)
                .message(message)
                .loginTime(LocalDateTime.now())
                .build());
    }

    private void recordSuccess(AuthUser user, String ip, String userAgent) {
        loginLogSink.save(LoginLogDTO.builder()
                .username(user.getUsername())
                .userId(user.getId())
                .userType(user.getUserType())
                .ip(ip)
                .os(userAgent)
                .status(0)
                .message("登录成功")
                .loginTime(LocalDateTime.now())
                .build());
    }

    private void clearFail(String account) {
        try {
            redisService.delete(lockKey(account));
        } catch (Exception e) {
            log.warn("登录失败计数清理失败：{}", e.getMessage());
        }
    }

    private static String lockKey(String account) {
        return CacheNames.LOGIN_FAIL + "::" + account;
    }

    private static Set<String> copyOf(List<String> list) {
        return list == null ? Set.of() : Set.copyOf(list);
    }

    /** 占位：UA 解析由 IpUtils 负责，这里只在需要时保留扩展点 */
    private static String browser(String userAgent) {
        return IpUtils.getUserAgent(null);
    }
}
