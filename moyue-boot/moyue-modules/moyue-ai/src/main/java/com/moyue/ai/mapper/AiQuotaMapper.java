package com.moyue.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.ai.domain.entity.AiQuota;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 配额 Mapper（AI 域）。
 *
 * @author moyue
 */
@Mapper
public interface AiQuotaMapper extends BaseMapper<AiQuota> {
}
