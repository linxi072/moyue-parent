package com.moyue.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.search.domain.entity.SearchHotWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 搜索热词数据访问。
 *
 * @author moyue
 */
@Mapper
public interface SearchHotWordMapper extends BaseMapper<SearchHotWord> {
}
