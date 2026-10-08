package com.moyue.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.ai.domain.dto.query.AiTaskQuery;
import com.moyue.ai.domain.entity.AiTask;
import com.moyue.ai.domain.vo.AiTaskVO;
import com.moyue.ai.mapper.AiTaskMapper;
import com.moyue.ai.service.AiTaskService;
import com.moyue.ai.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 域（任务）实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskServiceImpl implements AiTaskService {

    private final AiTaskMapper taskMapper;
    private final QuotaService quotaService;

    /** 单次任务固定消耗 token（占位计费，真实大模型按实际计费） */
    private static final int COST_TOKENS = 10;

    @Override
    public PageResult<AiTaskVO> pageTasks(AiTaskQuery query) {
        var page = PageUtils.<AiTask>page(query);
        var result = taskMapper.selectPage(page, new LambdaQueryWrapper<AiTask>()
                .like(StringUtils.hasText(query.getPrompt()), AiTask::getPrompt, query.getPrompt())
                .eq(query.getTaskType() != null, AiTask::getTaskType, query.getTaskType())
                .eq(query.getStatus() != null, AiTask::getStatus, query.getStatus())
                .eq(query.getUserId() != null, AiTask::getUserId, query.getUserId())
                .orderByDesc(AiTask::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(AiTask entity) {
        if (!StringUtils.hasText(entity.getPrompt())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "提示词不能为空");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(0);
        }
        taskMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTask(AiTask entity) {
        AiTask exist = taskMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("AI 任务");
        }
        return taskMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTask(Long id) {
        AiTask exist = taskMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("AI 任务");
        }
        return taskMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean run(Long taskId) {
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.notFound("AI 任务");
        }
        if (task.getStatus() != null && task.getStatus() != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务已执行，不能重复运行");
        }
        Long uid = task.getUserId() == null ? 0L : task.getUserId();
        // 先扣配额（不足抛 PAY_FAILED，事务回滚，任务保持待处理）
        quotaService.deduct(uid, COST_TOKENS);
        // 真实大模型调用为结构占位：生成占位结果
        String result = buildStub(task);
        AiTask upd = new AiTask();
        upd.setId(taskId);
        upd.setStatus(1);
        upd.setResult(result);
        upd.setCostTokens(COST_TOKENS);
        boolean ok = taskMapper.updateById(upd) > 0;
        log.info("AI 任务 {} 运行完成，消耗 {} token，用户 {}", taskId, COST_TOKENS, uid);
        return ok;
    }

    private String buildStub(AiTask task) {
        String typeName = taskTypeName(task.getTaskType());
        String prompt = task.getPrompt() == null ? "" : task.getPrompt();
        return String.format("【AI 模拟生成·%s】针对提示词「%s」的输出（沙箱未接入真实大模型，结果为占位示例，消耗 %d token）。",
                typeName, prompt, COST_TOKENS);
    }

    private String taskTypeName(Integer type) {
        return switch (type == null ? 0 : type) {
            case 1 -> "续写";
            case 2 -> "润色";
            case 3 -> "摘要";
            case 4 -> "大纲";
            default -> "通用";
        };
    }

    @Override
    public Map<String, Object> summary() {
        List<AiTask> all = taskMapper.selectList(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", (long) all.size());
        m.put("pending", all.stream().filter(t -> t.getStatus() != null && t.getStatus() == 0).count());
        m.put("success", all.stream().filter(t -> t.getStatus() != null && t.getStatus() == 1).count());
        m.put("failed", all.stream().filter(t -> t.getStatus() != null && t.getStatus() == 2).count());
        m.put("totalTokens", all.stream().filter(t -> t.getCostTokens() != null).mapToInt(AiTask::getCostTokens).sum());
        return m;
    }

    private AiTaskVO toVO(AiTask e) {
        return AiTaskVO.builder()
                .id(e.getId())
                .taskType(e.getTaskType())
                .prompt(e.getPrompt())
                .model(e.getModel())
                .status(e.getStatus())
                .result(e.getResult())
                .costTokens(e.getCostTokens())
                .userId(e.getUserId())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}
