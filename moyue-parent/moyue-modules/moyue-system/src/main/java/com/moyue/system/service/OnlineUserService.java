package com.moyue.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moyue.common.core.domain.PageResult;
import com.moyue.system.vo.OnlineUserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 在线用户业务：登录写 Redis 会话 + SCAN 分页查询 + 强退（删会话 + 写黑名单）。
 *
 * <p>Redis key 规约：</p>
 * <ul>
 *   <li>会话：{@code moyue:online:{md5(token)}}，value 为 JSON（用户ID/昵称/IP/登录时间/UA），
 *       TTL = accessToken 有效期；</li>
 *   <li>黑名单：{@code moyue:auth:deny:{md5(token)}}，TTL = 令牌剩余有效期，
 *       由 TokenDenyInterceptor 命中后 401。</li>
 * </ul>
 * <p>Redis 异常只 warn 不外抛——在线列表 / 会话写入失败不影响登录与业务。</p>
 */
@Service
public class OnlineUserService {

    private static final Logger log = LoggerFactory.getLogger(OnlineUserService.class);

    /** 在线会话 key 前缀 */
    public static final String ONLINE_KEY_PREFIX = "moyue:online:";

    /** 黑名单 key 前缀（与 TokenDenyInterceptor 保持一致） */
    public static final String DENY_KEY_PREFIX = "moyue:auth:deny:";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    @Autowired
    public OnlineUserService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 登录成功后写在线会话（登录埋点调用，全量 try-catch）。
     *
     * @param token    原始 accessToken（内部只存其 MD5）
     * @param userId   用户 ID
     * @param nickname 昵称
     * @param ip       登录 IP
     * @param userAgent 浏览器 UA
     * @param ttlMillis 会话 TTL（= accessToken 有效期毫秒）
     */
    public void addSession(String token, Long userId, String nickname, String ip, String userAgent, long ttlMillis) {
        if (token == null || token.isBlank() || ttlMillis <= 0) {
            return;
        }
        try {
            OnlineUserVO vo = new OnlineUserVO();
            vo.setTokenId(md5(token));
            vo.setUserId(userId);
            vo.setNickname(nickname);
            vo.setIp(ip);
            vo.setLoginTime(LocalDateTime.now());
            vo.setUserAgent(truncate(userAgent, 255));
            redisTemplate.opsForValue().set(ONLINE_KEY_PREFIX + vo.getTokenId(),
                    objectMapper.writeValueAsString(vo), ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[online] 写在线会话失败（不影响登录）：{}", e.getMessage());
        }
    }

    /** 在线用户分页（SCAN 遍历 moyue:online:*，keyword 匹配昵称/IP；内存分页，在线量级适用） */
    public PageResult<OnlineUserVO> page(int page, int size, String keyword) {
        List<OnlineUserVO> all = scanSessions();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim().toLowerCase();
            all = all.stream()
                    .filter(v -> (v.getNickname() != null && v.getNickname().toLowerCase().contains(k))
                            || (v.getIp() != null && v.getIp().toLowerCase().contains(k))
                            || (v.getTokenId() != null && v.getTokenId().contains(k)))
                    .collect(java.util.stream.Collectors.toList());
        }
        all.sort((a, b) -> b.getLoginTime() == null ? -1
                : a.getLoginTime() == null ? 1 : b.getLoginTime().compareTo(a.getLoginTime()));

        PageResult<OnlineUserVO> pr = new PageResult<>();
        pr.setTotal(all.size());
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        pr.setPage(page);
        pr.setSize(size);
        pr.setRecords(from >= to ? new ArrayList<>() : new ArrayList<>(all.subList(from, to)));
        return pr;
    }

    /**
     * 强退：删除在线会话 + 写黑名单（TTL = 令牌剩余有效期）。
     * 由 TokenDenyInterceptor 在后续请求命中黑名单时返回 401。
     */
    public void forceLogout(String tokenId) {
        String key = ONLINE_KEY_PREFIX + tokenId;
        Long ttl = null;
        try {
            ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[online] 读取会话 TTL 失败：{}", e.getMessage());
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[online] 删除会话失败：{}", e.getMessage());
        }
        if (ttl != null && ttl > 0) {
            try {
                redisTemplate.opsForValue().set(DENY_KEY_PREFIX + tokenId, "1", ttl, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("[online] 写黑名单失败（令牌将随 TTL 自然失效）：{}", e.getMessage());
            }
        }
        if (ttl != null && ttl <= 0) {
            // 会话已过期或不存在：按业务语义仍视为强退成功
            log.info("[online] 强退目标会话不存在或已过期：tokenId={}", tokenId);
        }
    }

    // ====================== 工具 ======================

    /** SCAN 遍历全部在线会话（COUNT=100 小步游标，避免阻塞 Redis） */
    private List<OnlineUserVO> scanSessions() {
        List<OnlineUserVO> result = new ArrayList<>();
        try {
            ScanOptions options = ScanOptions.scanOptions().match(ONLINE_KEY_PREFIX + "*").count(100).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    String value = redisTemplate.opsForValue().get(cursor.next());
                    if (value == null) {
                        continue;
                    }
                    try {
                        OnlineUserVO vo = objectMapper.readValue(value, OnlineUserVO.class);
                        result.add(vo);
                    } catch (Exception e) {
                        log.warn("[online] 解析会话 JSON 失败，跳过：{}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[online] SCAN 在线会话失败：{}", e.getMessage());
        }
        return result;
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    /** MD5 摘要（32 位小写十六进制），与 TokenDenyInterceptor 保持一致 */
    public static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }
}
