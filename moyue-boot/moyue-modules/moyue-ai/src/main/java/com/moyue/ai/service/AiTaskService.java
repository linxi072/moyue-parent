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
