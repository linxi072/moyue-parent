package com.moyue.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.auth.domain.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志数据访问。
 *
 * @author moyue
 */
@Mapper
public interface AuthLoginLogMapper extends BaseMapper<SysLoginLog> {
}
