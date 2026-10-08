package com.moyue.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.risk.domain.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词 Mapper（风控域）。
 *
 * @author moyue
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
}
