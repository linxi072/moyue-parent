package com.moyue.user.controller;

import com.moyue.api.account.dto.UserDTO;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.user.entity.UserEntity;
import com.moyue.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserController 关键路径回归测试（资料查询未命中的业务码文案、身份透传头解析）：
 * 纯 Mockito 单测，覆盖查询未命中时 Controller 抛错的异常码/文案，以及
 * X-User-Id / X-User-Role 头的解析与越权边界的转发行为。
 */
class UserControllerTest {

    private final UserService userService = mock(UserService.class);
    private final UserController controller = createController();

    private UserController createController() {
        UserController c = new UserController();
        ReflectionTestUtils.setField(c, "userService", userService);
        return c;
    }

    private HttpServletRequest headers(String userId, String role) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(Constants.USER_ID_HEADER)).thenReturn(userId);
        when(req.getHeader(Constants.USER_ROLE_HEADER)).thenReturn(role);
        return req;
    }

    private UserEntity entity(long id, String nickname, String avatarUrl, int role, int status) {
        UserEntity e = new UserEntity();
        e.setId(id);
        e.setPhone("13800009999");
        e.setNickname(nickname);
        e.setAvatarUrl(avatarUrl);
        e.setRole(role);
        e.setStatus(status);
        return e;
    }

    // ------------------------------ 资料查询 ------------------------------

    @Test
    @DisplayName("GET /users/{id} 命中：返回 code=0，data 的 id/nickname 映射正确")
    void getUserShouldReturnDtoWhenPresent() {
        when(userService.getUserById(5L))
                .thenReturn(entity(5L, "墨阅", "http://img/a.png", 1, 1));

        R<UserDTO> r = controller.getUser(5L);

        assertThat(r.getCode()).isZero();
        assertThat(r.getData().getId()).isEqualTo(5L);
        assertThat(r.getData().getNickname()).isEqualTo("墨阅");
    }

    @Test
    @DisplayName("GET /users/{id} 未命中：抛 RESOURCE_NOT_FOUND『资源不存在或已下架』")
    void getUserShouldThrowWhenAbsent() {
        when(userService.getUserById(404L)).thenReturn(null);

        assertThatThrownBy(() -> controller.getUser(404L))
                .isInstanceOf(BizException.class)
                .hasMessage(ResultCode.RESOURCE_NOT_FOUND.getMessage())
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
    }

    // ------------------------------ 资料更新（身份头解析） ------------------------------

    @Test
    @DisplayName("PUT /users/{id} 缺少 X-User-Id：抛 UNAUTHORIZED，不下发更新")
    void updateShouldRejectWhenUserIdHeaderMissing() {
        assertThatThrownBy(() -> controller.updateProfile(
                5L, new UserController.UpdateProfileRequest(), headers(null, "1")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
        verify(userService, never()).updateProfile(any(), anyLong(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("PUT /users/{id} X-User-Id 非数字：抛 UNAUTHORIZED")
    void updateShouldRejectWhenUserIdHeaderMalformed() {
        assertThatThrownBy(() -> controller.updateProfile(
                5L, new UserController.UpdateProfileRequest(), headers("abc", "1")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
        verify(userService, never()).updateProfile(any(), anyLong(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("PUT /users/{id} 合法请求：身份取 X-User-Id/X-User-Role 头并原样透传 Service")
    void updateShouldDelegateIdentityHeadersToService() {
        UserController.UpdateProfileRequest req = new UserController.UpdateProfileRequest();
        req.setNickname("新昵称");
        req.setAvatarUrl("http://img/new.png");
        when(userService.updateProfile(5L, 5L, 1, "新昵称", "http://img/new.png"))
                .thenReturn(entity(5L, "新昵称", "http://img/new.png", 1, 1));

        R<UserDTO> r = controller.updateProfile(5L, req, headers("5", "1"));

        assertThat(r.getCode()).isZero();
        assertThat(r.getData().getNickname()).isEqualTo("新昵称");
        verify(userService).updateProfile(5L, 5L, 1, "新昵称", "http://img/new.png");
    }

    @Test
    @DisplayName("PUT /users/{id} 角色头缺失：按角色 0 处理并透传")
    void updateShouldDefaultRoleToZeroWhenHeaderMissing() {
        when(userService.updateProfile(5L, 5L, 0, null, null))
                .thenReturn(entity(5L, "原昵称", null, 1, 1));

        R<UserDTO> r = controller.updateProfile(5L, new UserController.UpdateProfileRequest(), headers("5", null));

        assertThat(r.getCode()).isZero();
        verify(userService).updateProfile(5L, 5L, 0, null, null);
    }
}
