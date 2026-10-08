package com.moyue.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.system.domain.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * SysMenu 数据访问。
 *
 * @author moyue
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
}
