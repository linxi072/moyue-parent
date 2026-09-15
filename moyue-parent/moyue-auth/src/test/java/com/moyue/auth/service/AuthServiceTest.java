package com.moyue.auth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 关键路径回归测试（登录 / 注册 / 刷新 / 资料 / 演示用户播种）：
 * 纯 Mockito 单测，不启动 Spring 容器，也不依赖 Docker / Testcontainers。
 * 断言严格对齐源码文案与 {@link ResultCode} 业务码。
 */
class AuthServiceTest {

    /**
     * 预热 MyBatis-Plus 的 lambda 列名缓存。
     * AuthService 内部使用 {@code new LambdaQueryWrapper<AuthUserEntity>().eq(AuthUserEntity::getPhone, ...)}，
     * 纯 Mockito 单测下 MyBatis 从未初始化，会抛
     * "can not find lambda cache for this entity"。手动注册实体即可。
     */
    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AuthUserEntity.class);
    }

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String PHONE = "13800009999";
    private static final String RAW_PASSWORD = "p@ssw0rd";
    private static final String DEMO_PHONE = "13800000000";
    private static final String DEMO_PASSWORD = "123456";
    private static final long ACCESS_TTL = 7_200_000L;
    private static final long REFRESH_TTL = 604_800_000L;

    private final AuthUserMapper userMapper = mock(AuthUserMapper.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final AuthService service = createService();

    private AuthService createService() {
        AuthService s = new AuthService();
        ReflectionTestUtils.setField(s, "userMapper", userMapper);
        ReflectionTestUtils.setField(s, "jwtProvider", jwtProvider);
        ReflectionTestUtils.setField(s, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(s, "accessTtl", ACCESS_TTL);
        ReflectionTestUtils.setField(s, "refreshTtl", REFRESH_TTL);
        ReflectionTestUtils.setField(s, "demoPhone", DEMO_PHONE);
        ReflectionTestUtils.setField(s, "demoPassword", DEMO_PASSWORD);
        ReflectionTestUtils.setField(s, "demoNickname", "墨阅读者");
        ReflectionTestUtils.setField(s, "demoRole", 1);
        return s;
    }

    // ------------------------------ fixtures ------------------------------

    private AuthUserEntity user(long id, String rawPassword, int status, int role) {
        AuthUserEntity u = new AuthUserEntity();
        u.setId(id);
        u.setPhone(PHONE);
        u.setNickname("测试用户");
        u.setPassword(ENCODER.encode(rawPassword));
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    private LoginReq loginReq(String phone, String password) {
        LoginReq r = new LoginReq();
        r.setPhone(phone);
        r.setPassword(password);
        return r;
    }

    private RegisterReq registerReq(String phone) {
        RegisterReq r = new RegisterReq();
        r.setPhone(phone);
        r.setCode("1234");
        r.setPassword(RAW_PASSWORD);
        return r;
    }

    private RefreshReq refreshReq(String token) {
        RefreshReq r = new RefreshReq();
        r.setRefreshToken(token);
        return r;
    }

    private static JwtProvider.JwtClaims refreshClaims(long userId) {
        JwtProvider.JwtClaims c = new JwtProvider.JwtClaims();
        c.setUserId(userId);
        c.setType(Constants.TOKEN_TYPE_REFRESH);
        return c;
    }

    private void stubTokenIssue() {
        when(jwtProvider.createAccessToken(any(), any(), anyLong())).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(any(), anyLong())).thenReturn("refresh-token");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ------------------------------ login ------------------------------

    @Test
    @DisplayName("login 成功：签发双令牌，并以 moyue:refresh:<token> / userId / refreshTtl 毫秒写入 Redis")
    void loginShouldIssueTokensAndCacheRefreshToken() {
        when(userMapper.selectOne(any())).thenReturn(user(1L, RAW_PASSWORD, 1, 1));
        stubTokenIssue();

        LoginVO vo = service.login(loginReq(PHONE, RAW_PASSWORD));

        assertThat(vo.getAccessToken()).isEqualTo("access-token");
        assertThat(vo.getRefreshToken()).isEqualTo("refresh-token");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unit = ArgumentCaptor.forClass(TimeUnit.class);
        verify(valueOps).set(key.capture(), value.capture(), ttl.capture(), unit.capture());
        assertThat(key.getValue()).isEqualTo("moyue:refresh:refresh-token");
        assertThat(value.getValue()).isEqualTo("1");
        assertThat(ttl.getValue()).isEqualTo(REFRESH_TTL);
        assertThat(unit.getValue()).isEqualTo(TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("login 手机号查不到：抛 PARAM_ERROR，文案『手机号或密码错误』")
    void loginShouldFailWhenPhoneNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.login(loginReq(PHONE, RAW_PASSWORD)))
                .isInstanceOf(BizException.class)
                .hasMessage("手机号或密码错误")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    @DisplayName("login 密码与 BCrypt 哈希不匹配：抛 PARAM_ERROR，文案『手机号或密码错误』")
    void loginShouldFailWhenPasswordMismatch() {
        when(userMapper.selectOne(any())).thenReturn(user(1L, "correct-password", 1, 1));

        assertThatThrownBy(() -> service.login(loginReq(PHONE, "wrong-password")))
                .isInstanceOf(BizException.class)
                .hasMessage("手机号或密码错误")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    @DisplayName("login 账号 status=0：抛 FORBIDDEN，文案『账号已禁用』")
    void loginShouldFailWhenAccountDisabled() {
        when(userMapper.selectOne(any())).thenReturn(user(1L, RAW_PASSWORD, 0, 1));

        assertThatThrownBy(() -> service.login(loginReq(PHONE, RAW_PASSWORD)))
                .isInstanceOf(BizException.class)
                .hasMessage("账号已禁用")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    // ------------------------------ register ------------------------------

    @Test
    @DisplayName("register 手机号已存在：抛 PARAM_ERROR『该手机号已注册』，且不写入 user 表")
    void registerShouldRejectWhenPhoneExists() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.register(registerReq(PHONE)))
                .isInstanceOf(BizException.class)
                .hasMessage("该手机号已注册")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verify(userMapper, never()).insert(any(AuthUserEntity.class));
    }

    @Test
    @DisplayName("register 成功：role=1、status=1、昵称『墨阅用户』+手机号后4位、密码为 BCrypt 密文")
    void registerShouldInsertBcryptUser() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        service.register(registerReq(PHONE));

        ArgumentCaptor<AuthUserEntity> captor = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userMapper).insert(captor.capture());
        AuthUserEntity saved = captor.getValue();
        assertThat(saved.getPhone()).isEqualTo(PHONE);
        assertThat(saved.getRole()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getNickname()).isEqualTo("墨阅用户" + PHONE.substring(7));
        assertThat(saved.getPassword()).isNotEqualTo(RAW_PASSWORD);
        assertThat(ENCODER.matches(RAW_PASSWORD, saved.getPassword())).isTrue();
    }

    // ------------------------------ refresh ------------------------------

    @Test
    @DisplayName("refresh token 类型非 refresh（如 access）：抛 TOKEN_INVALID")
    void refreshShouldRejectNonRefreshToken() {
        JwtProvider.JwtClaims claims = new JwtProvider.JwtClaims();
        claims.setUserId(1L);
        claims.setType(Constants.TOKEN_TYPE_ACCESS);
        when(jwtProvider.parse("access-token")).thenReturn(claims);

        assertThatThrownBy(() -> service.refresh(refreshReq("access-token")))
                .isInstanceOf(BizException.class)
                .hasMessage(ResultCode.TOKEN_INVALID.getMessage())
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.TOKEN_INVALID.getCode());
    }

    @Test
    @DisplayName("refresh Redis 无该 token 缓存：抛 TOKEN_EXPIRED『refreshToken 已失效，请重新登录』")
    void refreshShouldFailWhenCacheMissing() {
        when(jwtProvider.parse("refresh-token")).thenReturn(refreshClaims(1L));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("moyue:refresh:refresh-token")).thenReturn(null);

        assertThatThrownBy(() -> service.refresh(refreshReq("refresh-token")))
                .isInstanceOf(BizException.class)
                .hasMessage("refreshToken 已失效，请重新登录")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.TOKEN_EXPIRED.getCode());
    }

    @Test
    @DisplayName("refresh 缓存命中但用户不存在：抛 UNAUTHORIZED『用户不存在』")
    void refreshShouldFailWhenUserMissing() {
        when(jwtProvider.parse("refresh-token")).thenReturn(refreshClaims(9L));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("moyue:refresh:refresh-token")).thenReturn("9");
        when(userMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.refresh(refreshReq("refresh-token")))
                .isInstanceOf(BizException.class)
                .hasMessage("用户不存在")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("refresh 成功：重新签发双令牌并再次以新 token 为 key 写入 Redis")
    void refreshShouldReissueTokensAndRecache() {
        when(jwtProvider.parse("refresh-token")).thenReturn(refreshClaims(1L));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("moyue:refresh:refresh-token")).thenReturn("1");
        when(userMapper.selectById(1L)).thenReturn(user(1L, RAW_PASSWORD, 1, 1));
        when(jwtProvider.createAccessToken(any(), any(), anyLong())).thenReturn("new-access");
        when(jwtProvider.createRefreshToken(any(), anyLong())).thenReturn("new-refresh");

        LoginVO vo = service.refresh(refreshReq("refresh-token"));

        assertThat(vo.getAccessToken()).isEqualTo("new-access");
        assertThat(vo.getRefreshToken()).isEqualTo("new-refresh");
        verify(valueOps).set(eq("moyue:refresh:new-refresh"), eq("1"), eq(REFRESH_TTL), eq(TimeUnit.MILLISECONDS));
    }

    // ------------------------------ me ------------------------------

    @Test
    @DisplayName("me 用户不存在：抛 UNAUTHORIZED『用户不存在』")
    void meShouldFailWhenUserMissing() {
        when(userMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.me(404L))
                .isInstanceOf(BizException.class)
                .hasMessage("用户不存在")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("me 用户存在：id/phone/nickname/role/status 逐字段映射正确")
    void meShouldMapAllFields() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, RAW_PASSWORD, 1, 2));

        UserInfoVO vo = service.me(1L);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getPhone()).isEqualTo(PHONE);
        assertThat(vo.getNickname()).isEqualTo("测试用户");
        assertThat(vo.getRole()).isEqualTo(2);
        assertThat(vo.getStatus()).isEqualTo(1);
    }

    // ------------------------------ run（演示用户播种） ------------------------------

    @Test
    @DisplayName("run 演示用户已存在：不重复 insert")
    void runShouldSkipWhenDemoUserExists() {
        when(userMapper.selectOne(any())).thenReturn(user(1L, DEMO_PASSWORD, 1, 1));

        service.run();

        verify(userMapper, never()).insert(any(AuthUserEntity.class));
    }

    @Test
    @DisplayName("run 演示用户不存在：以 id=1、BCrypt 密码写入演示账号")
    void runShouldSeedDemoUserWithBcryptPassword() {
        when(userMapper.selectOne(any())).thenReturn(null);

        service.run();

        ArgumentCaptor<AuthUserEntity> captor = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userMapper).insert(captor.capture());
        AuthUserEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getPhone()).isEqualTo(DEMO_PHONE);
        assertThat(saved.getNickname()).isEqualTo("墨阅读者");
        assertThat(saved.getRole()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getPassword()).isNotEqualTo(DEMO_PASSWORD);
        assertThat(ENCODER.matches(DEMO_PASSWORD, saved.getPassword())).isTrue();
    }
}
