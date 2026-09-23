package com.moyue.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import com.moyue.system.entity.SysConfigEntity;
import com.moyue.system.mapper.SysConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统参数业务：参数的增删改查、按 key 取值（Redis 缓存）与缓存刷新。
 *
 * <p>缓存名 {@code system:config}（最终 Redis key 形如 {@code moyue:system:config::key:...}）；
 * 内置参数（is_system=1）不可删除；缓存异常自动回源数据库。</p>
 */
@Service
public class SysConfigService {

    /** 参数缓存名（对齐 moyue:system:config:* key 规约） */
    public static final String CACHE_CONFIG = "system:config";

    @Autowired
    private SysConfigMapper configMapper;

    // ====================== 查询 ======================

    /** 参数分页（可选 keyword 匹配名称/键名） */
    public PageResult<SysConfigEntity> page(int page, int size, String keyword) {
        Page<SysConfigEntity> param = new Page<>(page, size);
        LambdaQueryWrapper<SysConfigEntity> q = Wrappers.<SysConfigEntity>lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like(SysConfigEntity::getConfigName, k).or().like(SysConfigEntity::getConfigKey, k));
        }
        q.orderByAsc(SysConfigEntity::getId);
        IPage<SysConfigEntity> result = configMapper.selectPage(param, q);

        PageResult<SysConfigEntity> pr = new PageResult<>();
        pr.setTotal(result.getTotal());
        pr.setPage(page);
        pr.setSize(size);
        pr.setRecords(result.getRecords());
        return pr;
    }

    public SysConfigEntity get(Long id) {
        SysConfigEntity e = configMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "参数不存在");
        }
        return e;
    }

    /** 按键名取参数（走 Redis 缓存） */
    @Cacheable(cacheNames = CACHE_CONFIG, key = "'key:' + #configKey", unless = "#result == null")
    public SysConfigEntity getByKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "参数键名不能为空");
        }
        SysConfigEntity e = configMapper.selectOne(Wrappers.<SysConfigEntity>lambdaQuery()
                .eq(SysConfigEntity::getConfigKey, configKey.trim()));
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "参数不存在：" + configKey);
        }
        return e;
    }

    /** 按键名取参数值（便捷方法） */
    public String getValueByKey(String configKey) {
        SysConfigEntity e = getByKey(configKey);
        return e == null ? null : e.getConfigValue();
    }

    // ====================== 写操作 ======================

    /** 校验 config_key 唯一（写路径清空参数缓存，保证 GET /config/key/{key} 即时一致） */
    @Transactional
    @CacheEvict(cacheNames = CACHE_CONFIG, allEntries = true)
    public SysConfigEntity create(String configName, String configKey, String configValue,
                                  Integer isSystem, String remark) {
        if (configName == null || configName.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "参数名称不能为空");
        }
        if (configKey == null || configKey.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "参数键名不能为空");
        }
        String key = configKey.trim();
        if (configMapper.selectCount(Wrappers.<SysConfigEntity>lambdaQuery()
                .eq(SysConfigEntity::getConfigKey, key)) > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "参数键名已存在：" + key);
        }
        SysConfigEntity e = new SysConfigEntity();
        e.setConfigName(configName.trim());
        e.setConfigKey(key);
        e.setConfigValue(configValue);
        e.setIsSystem(isSystem == null ? 0 : isSystem);
        e.setRemark(remark);
        configMapper.insert(e);
        return e;
    }

    /** 修改参数（写路径清空参数缓存；key 变更后新旧两个键都要失效，allEntries 覆盖） */
    @Transactional
    @CacheEvict(cacheNames = CACHE_CONFIG, allEntries = true)
    public SysConfigEntity update(Long id, String configName, String configKey, String configValue, String remark) {
        SysConfigEntity e = get(id);
        if (configName != null) {
            if (configName.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "参数名称不能为空");
            }
            e.setConfigName(configName.trim());
        }
        if (configKey != null) {
            if (configKey.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "参数键名不能为空");
            }
            String key = configKey.trim();
            if (!key.equals(e.getConfigKey())
                    && configMapper.selectCount(Wrappers.<SysConfigEntity>lambdaQuery()
                    .eq(SysConfigEntity::getConfigKey, key)
                    .ne(SysConfigEntity::getId, id)) > 0) {
                throw new BizException(ResultCode.PARAM_ERROR, "参数键名已存在：" + key);
            }
            e.setConfigKey(key);
        }
        if (configValue != null) e.setConfigValue(configValue);
        if (remark != null) e.setRemark(remark);
        configMapper.updateById(e);
        return e;
    }

    /** 删除参数（内置参数 is_system=1 不可删；写路径清空参数缓存） */
    @Transactional
    @CacheEvict(cacheNames = CACHE_CONFIG, allEntries = true)
    public void delete(Long id) {
        SysConfigEntity e = get(id);
        if (e.getIsSystem() != null && e.getIsSystem() == 1) {
            throw new BizException(ResultCode.FORBIDDEN, "内置参数不允许删除：" + e.getConfigKey());
        }
        configMapper.deleteById(id);
    }

    // ====================== 缓存刷新 ======================

    /** 刷新参数缓存：清空 system:config 全部缓存条目 */
    @CacheEvict(cacheNames = CACHE_CONFIG, allEntries = true)
    public void refreshCache() {
        // 仅清缓存，无需其它动作
    }
}
