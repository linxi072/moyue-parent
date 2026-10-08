package com.moyue.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.ai.domain.entity.AiTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 任务数据访问。
 *
 * @author moyue
 */
@Mapper
public interface AiTaskMapper extends BaseMapper<AiTask> {
}
