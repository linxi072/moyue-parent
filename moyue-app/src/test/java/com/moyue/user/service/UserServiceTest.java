package com.moyue.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.account.dto.UserDTO;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import com.moyue.user.entity.UserEntity;
import com.moyue.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 关键路径回归测试（资料查询 / 分页 / 更新权限 / Entity-DTO 映射）：
 * 纯 Mockito 单测，不启动 Spring 容器，也不依赖 Docker / Testcontainers。
 * UserService 内部未使用 LambdaQueryWrapper，故无需预热 MyBatis-Plus lambda 缓存。
 */
class UserServiceTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 99L;
    private static final int ROLE_READER = 1;
    private static final int ROLE_ADMIN = 3;

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserService service = createService();

    private UserService createService() {
        UserService s = new UserService();
        ReflectionTestUtils.setField(s, "userMapper", userMapper);
        return s;
    }

    private UserEntity entity(long id, String nickname, String avatarUrl, int role, int status) {
        UserEntity e = new UserEntity();
        e.setId(id);
        e.setPhone("13800009999");
        e.setNickname(nickname);
        e.setAvatarUrl(avatarUrl);
        e.setRole(role);
        e.setStatus(status);
        e.setIsDeleted(0);
        return e;
    }

    // ------------------------------ 资料查询 ------------------------------

    @Test
    @DisplayName("getUserById 命中：id/phone/nickname/avatarUrl/role/status 逐字段正确")
    void getUserByIdShouldReturnEntityWhenPresent() {
        when(userMapper.selectById(USER_ID))
                .thenReturn(entity(USER_ID, "墨阅读者", "http://img/a.png", ROLE_READER, 1));

        UserEntity got = service.getUserById(USER_ID);

        assertThat(got.getId()).isEqualTo(USER_ID);
        assertThat(got.getPhone()).isEqualTo("13800009999");
        assertThat(got.getNickname()).isEqualTo("墨阅读者");
        assertThat(got.getAvatarUrl()).isEqualTo("http://img/a.png");
        assertThat(got.getRole()).isEqualTo(ROLE_READER);
        assertThat(got.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("getUserById 未命中：Service 返回 null（RESOURCE_NOT_FOUND 由 Controller 层抛出）")
    void getUserByIdShouldReturnNullWhenAbsent() {
        when(userMapper.selectById(404L)).thenReturn(null);

        assertThat(service.getUserById(404L)).isNull();
    }

    @Test
    @DisplayName("pageUsers 分页：保留页号/页大小，并回填 total 与记录")
    void pageUsersShouldReturnPageMetaAndRecords() {
        UserEntity a = entity(1L, "A", null, ROLE_READER, 1);
        UserEntity b = entity(2L, "B", null, ROLE_READER, 1);
        when(userMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<UserEntity> p = invocation.getArgument(0);
            p.setRecords(List.of(a, b));
            p.setTotal(2L);
            return p;
        });

        PageResult<UserEntity> result = service.pageUsers(1, 20);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getRecords()).containsExactly(a, b);
    }

    // ------------------------------ 资料更新（权限校验） ------------------------------

    @Test
    @DisplayName("updateProfile 目标用户不存在：抛 RESOURCE_NOT_FOUND『资源不存在或已下架』，不调用 update")
    void updateProfileShouldFailWhenUserMissing() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.updateProfile(USER_ID, USER_ID, ROLE_READER, "新昵称", null))
                .isInstanceOf(BizException.class)
                .hasMessage(ResultCode.RESOURCE_NOT_FOUND.getMessage())
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    @DisplayName("updateProfile 越权：普通用户改他人资料 → FORBIDDEN，不调用 update")
    void updateProfileShouldForbidNonOwner() {
        when(userMapper.selectById(USER_ID))
                .thenReturn(entity(USER_ID, "原昵称", null, ROLE_READER, 1));

        assertThatThrownBy(() -> service.updateProfile(USER_ID, OTHER_USER_ID, ROLE_READER, "新昵称", null))
                .isInstanceOf(BizException.class)
                .hasMessage(ResultCode.FORBIDDEN.getMessage())
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    @DisplayName("updateProfile 本人改自己资料：update 被调用且昵称/头像落库（昵称 trim）")
    void updateProfileShouldAllowOwnerSelfUpdate() {
        UserEntity e = entity(USER_ID, "原昵称", "http://img/old.png", ROLE_READER, 1);
        when(userMapper.selectById(USER_ID)).thenReturn(e);

        UserEntity updated =
                service.updateProfile(USER_ID, USER_ID, ROLE_READER, "  新昵称  ", "http://img/new.png");

        verify(userMapper).updateById(e);
        assertThat(updated.getNickname()).isEqualTo("新昵称");
        assertThat(updated.getAvatarUrl()).isEqualTo("http://img/new.png");
    }

    @Test
    @DisplayName("updateProfile 管理员：可改他人资料，update 被调用且字段正确")
    void updateProfileShouldAllowAdminToUpdateOthers() {
        UserEntity e = entity(USER_ID, "原昵称", null, ROLE_READER, 1);
        when(userMapper.selectById(USER_ID)).thenReturn(e);

        UserEntity updated =
                service.updateProfile(USER_ID, OTHER_USER_ID, ROLE_ADMIN, "新昵称", "http://img/new.png");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("新昵称");
        assertThat(captor.getValue().getAvatarUrl()).isEqualTo("http://img/new.png");
        assertThat(updated.getNickname()).isEqualTo("新昵称");
    }

    @Test
    @DisplayName("updateProfile 昵称为空白：抛 PARAM_ERROR『昵称不能为空』，不调用 update")
    void updateProfileShouldRejectBlankNickname() {
        when(userMapper.selectById(USER_ID))
                .thenReturn(entity(USER_ID, "原昵称", null, ROLE_READER, 1));

        assertThatThrownBy(() -> service.updateProfile(USER_ID, USER_ID, ROLE_READER, "   ", null))
                .isInstanceOf(BizException.class)
                .hasMessage("昵称不能为空")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verify(userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    @DisplayName("updateProfile 昵称为 null：保留原昵称，仅更新头像")
    void updateProfileShouldKeepNicknameWhenNull() {
        UserEntity e = entity(USER_ID, "原昵称", "http://img/old.png", ROLE_READER, 1);
        when(userMapper.selectById(USER_ID)).thenReturn(e);

        UserEntity updated =
                service.updateProfile(USER_ID, USER_ID, ROLE_READER, null, "http://img/new.png");

        assertThat(updated.getNickname()).isEqualTo("原昵称");
        assertThat(updated.getAvatarUrl()).isEqualTo("http://img/new.png");
    }

    // ------------------------------ Entity -> DTO ------------------------------

    @Test
    @DisplayName("toDto 逐字段映射：id/phone/nickname/avatarUrl/role/status")
    void toDtoShouldMapAllFields() {
        UserEntity e = entity(USER_ID, "墨阅读者", "http://img/a.png", ROLE_ADMIN, 0);

        UserDTO dto = UserService.toDto(e);

        assertThat(dto.getId()).isEqualTo(USER_ID);
        assertThat(dto.getPhone()).isEqualTo("13800009999");
        assertThat(dto.getNickname()).isEqualTo("墨阅读者");
        assertThat(dto.getAvatarUrl()).isEqualTo("http://img/a.png");
        assertThat(dto.getRole()).isEqualTo(ROLE_ADMIN);
        assertThat(dto.getStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("toDto 入参为 null：返回 null")
    void toDtoShouldReturnNullForNullEntity() {
        assertThat(UserService.toDto(null)).isNull();
    }

    @Test
    @DisplayName("toDtoPage 保留分页元信息并逐条转换记录")
    void toDtoPageShouldMapPageMetaAndRecords() {
        PageResult<UserEntity> page = new PageResult<>();
        page.setTotal(2L);
        page.setPage(1);
        page.setSize(20);
        page.setRecords(List.of(
                entity(1L, "A", null, ROLE_READER, 1),
                entity(2L, "B", null, ROLE_READER, 1)));

        PageResult<UserDTO> dto = UserService.toDtoPage(page);

        assertThat(dto.getTotal()).isEqualTo(2L);
        assertThat(dto.getPage()).isEqualTo(1);
        assertThat(dto.getSize()).isEqualTo(20);
        assertThat(dto.getRecords()).hasSize(2);
        assertThat(dto.getRecords().get(0).getNickname()).isEqualTo("A");
        assertThat(dto.getRecords().get(1).getNickname()).isEqualTo("B");
    }
}
