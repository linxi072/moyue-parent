package com.moyue.ai.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.ai.domain.dto.query.AiQuotaQuery;
import com.moyue.ai.domain.entity.AiQuota;
import com.moyue.ai.domain.vo.AiQuotaVO;

/**
 * AI 配额管理（运营端）：列表 / 重置 / 扣减。
 *
 * @author moyue
 */
public interface QuotaService {

    PageResult<AiQuotaVO> pageQuotas(AiQuotaQuery query);

    /** 获取或初始化用户配额（默认总额 1000） */
    AiQuota getOrCreate(Long userId);

    /** 我的配额视图（C 端，自动初始化后返回 VO） */
    AiQuotaVO myQuota(Long userId);

    /** 扣减配额；不足抛出 PAY_FAILED 异常（事务回滚） */
    void deduct(Long userId, int amount);

    /** 重置用户配额（used=0, remain=total） */
    void reset(Long userId, int total);
}
