package com.moyue.system.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.dto.query.ConfigQuery;
import com.moyue.system.domain.entity.SysConfig;
import com.moyue.system.domain.vo.ConfigVO;

import java.util.List;

/**
 * 参数管理域（⑥）服务。
 *
 * @author moyue
 */
public interface SysConfigService {

    PageResult<ConfigVO> pageConfigs(ConfigQuery query);

    Long createConfig(SysConfig entity);

    boolean updateConfig(SysConfig entity);

    /** 删除参数（config_type = 1 系统内置不可删） */
    boolean deleteConfig(Long configId);

    /**
     * 按键名取参数值（走缓存）。
     *
     * @param configKey 参数键名
     * @return 参数值，不存在返回 null
     */
    String getValueByKey(String configKey);
}
