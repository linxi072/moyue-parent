package com.moyue.ai.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.security.context.UserContext;
import com.moyue.ai.domain.dto.query.AiTaskQuery;
import com.moyue.ai.domain.vo.AiQuotaVO;
import com.moyue.ai.domain.vo.AiTaskVO;
import com.moyue.ai.service.AiTaskService;
import com.moyue.ai.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 任务与配额（C 端用户）。
 *
 * <p>身份取自 {@link UserContext}（网关注入头派生），所有查询归属当前登录用户，
 * 不接受前端传入 userId（与站内信收件箱等 C 端接口一致的架构约定）；任务详情按归属校验，
 * 非本人任务按「不存在」处理。网关路由 {@code /api/v1/ai/**} 已指向本服务，无需新增路由。
 *
 * @author moyue
 */
@Tag(name = "AI 任务与配额", description = "我的任务列表、任务详情、我的配额")
@Validated
@RestController
@RequestMapping(Constants.API_PREFIX + "/ai")
@RequiredArgsConstructor
public class AiConsumerController {

    private final AiTaskService taskService;
    private final QuotaService quotaService;

    @Operation(summary = "我的 AI 任务列表")
    @GetMapping("/tasks")
    public R<PageResult<AiTaskVO>> tasks(AiTaskQuery query) {
        return R.ok(taskService.pageMyTasks(currentUser(), query));
    }

    @Operation(summary = "我的 AI 任务详情", description = "含状态 / 结果 / 消耗 token；非本人任务按不存在处理")
    @GetMapping("/tasks/{id}")
    public R<AiTaskVO> task(@PathVariable Long id) {
        return R.ok(taskService.getMyTask(currentUser(), id));
    }

    @Operation(summary = "我的 AI 配额", description = "首次查询自动初始化（默认 1000 token）")
    @GetMapping("/quota")
    public R<AiQuotaVO> quota() {
        return R.ok(quotaService.myQuota(currentUser()));
    }

    private Long currentUser() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }
}
