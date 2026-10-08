package com.moyue.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.system.domain.entity.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * SysOperLog 数据访问。
 *
 * @author moyue
 */
@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
