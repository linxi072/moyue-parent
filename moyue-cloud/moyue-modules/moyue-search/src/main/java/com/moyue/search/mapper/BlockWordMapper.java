package com.moyue.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.search.domain.entity.BlockWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 搜索屏蔽词数据访问。
 *
 * @author moyue
 */
@Mapper
public interface BlockWordMapper extends BaseMapper<BlockWord> {
}
