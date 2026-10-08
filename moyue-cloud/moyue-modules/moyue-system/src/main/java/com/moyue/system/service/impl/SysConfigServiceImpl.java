package com.moyue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.redis.constant.CacheNames;
import com.moyue.common.redis.service.RedisService;
import com.moyue.system.domain.dto.query.ConfigQuery;
import com.moyue.system.domain.entity.SysConfig;
import com.moyue.system.domain.vo.ConfigVO;
import com.moyue.system.mapper.SysConfigMapper;
import com.moyue.system.service.SysConfigService;
import com.moyue.system.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

/**
 * 参数管理域（⑥）实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigMapper configMapper;
    private final RedisService redisService;

    private static String cacheKey(String configKey) {
        return CacheNames.SYS_CONFIG + ":" + configKey;
    }

    @Override
    public PageResult<ConfigVO> pageConfigs(ConfigQuery query) {
        var page = PageUtils.<SysConfig>page(query);
        var result = configMapper.selectPage(page, new LambdaQueryWrapper<SysConfig>()
                .like(StringUtils.hasText(query.getConfigName()), SysConfig::getConfigName, query.getConfigName())
                .like(StringUtils.hasText(query.getConfigKey()), SysConfig::getConfigKey, query.getConfigKey())
                .eq(query.getConfigType() != null, SysConfig::getConfigType, query.getConfigType())
                .orderByAsc(SysConfig::getId));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createConfig(SysConfig entity) {
        checkKeyUnique(entity.getConfigKey(), null);
        if (entity.getConfigType() == null) {
            entity.setConfigType(0);
        }
        configMapper.insert(entity);
        evict(entity.getConfigKey());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateConfig(SysConfig entity) {
        SysConfig exist = configMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("参数");
        }
        checkKeyUnique(entity.getConfigKey(), entity.getId());
        evict(exist.getConfigKey());
        evict(entity.getConfigKey());
        return configMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConfig(Long configId) {
        SysConfig exist = configMapper.selectById(configId);
        if (exist == null) {
            throw BusinessException.notFound("参数");
        }
        if (exist.getConfigType() != null && exist.getConfigType() == 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统内置参数不可删除");
        }
        evict(exist.getConfigKey());
        return configMapper.deleteById(configId) > 0;
    }

    @Override
    public String getValueByKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return null;
        }
        String key = cacheKey(configKey);
        try {
            Object cached = redisService.get(key);
            if (cached != null) {
                return String.valueOf(cached);
            }
        } catch (Exception e) {
            log.warn("参数缓存读取失败，回退数据库：{}", e.getMessage());
        }
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey));
        if (config == null) {
            return null;
        }
        try {
            redisService.set(key, config.getConfigValue());
        } catch (Exception e) {
            log.warn("参数缓存写入失败：{}", e.getMessage());
        }
        return config.getConfigValue();
    }

    private ConfigVO toVO(SysConfig entity) {
        return ConfigVO.builder()
                .id(entity.getId())
                .configName(entity.getConfigName())
                .configKey(entity.getConfigKey())
                .configValue(entity.getConfigValue())
                .configType(entity.getConfigType())
                .createTime(entity.getCreateTime())
                .remark(entity.getRemark())
                .build();
    }

    private void checkKeyUnique(String configKey, Long excludeId) {
        if (!StringUtils.hasText(configKey)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "参数键名不能为空");
        }
        Long count = configMapper.selectCount(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey)
                .ne(excludeId != null, SysConfig::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "参数键名已存在：" + configKey);
        }
    }

    private void evict(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return;
        }
        try {
            redisService.delete(cacheKey(configKey));
        } catch (Exception e) {
            log.warn("参数缓存失效失败：{}", e.getMessage());
        }
    }
}
