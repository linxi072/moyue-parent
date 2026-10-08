package com.moyue.system.api.fallback;

import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.R;
import com.moyue.system.api.SystemUserApi;
import com.moyue.system.api.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户域调用降级实现。
 *
 * <p>策略：<strong>返回降级码而非抛异常</strong>。用户信息属于「增强信息」而非
 * 主链路数据，拿不到时应让调用方继续跑（展示缺省昵称），而不是让整个接口失败。
 *
 * @author moyue
 */
@Slf4j
@Component
public class SystemUserApiFallback implements SystemUserApi {

    @Override
    public R<UserDTO> getUser(Long userId) {
        log.warn("用户服务不可用，降级返回空用户：userId={}", userId);
        return R.fail(ErrorCode.SERVICE_DEGRADED, "用户服务不可用，已降级处理");
    }

    @Override
    public R<List<UserDTO>> listByIds(List<Long> ids) {
        log.warn("用户服务不可用，降级返回空列表：size={}", ids == null ? 0 : ids.size());
        return R.fail(ErrorCode.SERVICE_DEGRADED, "用户服务不可用，已降级处理");
    }
}
