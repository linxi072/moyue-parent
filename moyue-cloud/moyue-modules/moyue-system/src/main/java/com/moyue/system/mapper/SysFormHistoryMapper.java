package com.moyue.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.system.domain.entity.SysFormHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单历史版本数据访问。
 *
 * @author moyue
 */
@Mapper
public interface SysFormHistoryMapper extends BaseMapper<SysFormHistory> {
}
