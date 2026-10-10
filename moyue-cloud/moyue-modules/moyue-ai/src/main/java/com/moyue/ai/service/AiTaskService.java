package com.moyue.ai.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.ai.domain.dto.query.AiTaskQuery;
import com.moyue.ai.domain.entity.AiTask;
import com.moyue.ai.domain.vo.AiTaskVO;

import java.util.Map;

/**
 * AI 域（任务）服务。
 *
 * @author moyue
 */
public interface AiTaskService {

    PageResult<AiTaskVO> pageTasks(AiTaskQuery query);

    /**
     * 当前用户的 AI 任务列表（C 端）。
     *
     * <p>强制按 {@code userId} 限定发起用户，忽略调用方传入的 userId，避免越权查看他人任务。
     */
    PageResult<AiTaskVO> pageMyTasks(Long userId, AiTaskQuery query);

    /**
     * 当前用户的单个 AI 任务详情（C 端）。
     *
     * <p>按 {@code userId} 校验任务归属，非本人任务按「不存在」处理，不泄露他人任务内容。
     *
     * @return 任务视图（含状态 / 结果 / 消耗 token）
     */
    AiTaskVO getMyTask(Long userId, Long id);

    Long createTask(AiTask entity);

    boolean updateTask(AiTask entity);

    boolean deleteTask(Long id);

    /** 任务概览：总数 / 待处理 / 成功 / 失败 / 总 token 消耗 */
    Map<String, Object> summary();

    /**
     * 运行任务：状态 0 待处理 → 1 成功（写结果 + 扣配额）；配额不足则拒绝并保持待处理。
     * 真实大模型调用为结构占位（沙箱无凭证），仅生成占位结果并扣减 AI 配额。
     */
    boolean run(Long taskId);
}
