package com.moyue.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.ai.entity.AiSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/** AI 客服会话 Mapper */
@Mapper
public interface AiSessionMapper extends BaseMapper<AiSessionEntity> {
}
