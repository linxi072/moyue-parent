package com.moyue.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.exception.BizException;
import com.moyue.common.core.domain.ResultCode;
import com.moyue.common.core.domain.PageResult;
import com.moyue.system.entity.SysDictDataEntity;
import com.moyue.system.entity.SysDictTypeEntity;
import com.moyue.system.mapper.SysDictDataMapper;
import com.moyue.system.mapper.SysDictTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字典管理业务：类型 + 数据的增删改查、按类型取启用数据（Redis 缓存）与缓存刷新。
 *
 * <p>缓存名 {@code system:dict}（经 common 的 MoyueCacheAutoConfiguration 加前缀，
 * 最终 Redis key 形如 {@code moyue:system:dict::data:novel_status}）；
 * 缓存异常自动回源数据库，Redis 不可用时业务不中断。</p>
 */
@Service
public class SysDictService {

    /** 字典缓存名（对齐 moyue:system:dict:* key 规约） */
    public static final String CACHE_DICT = "system:dict";

    @Autowired
    private SysDictTypeMapper dictTypeMapper;

    @Autowired
    private SysDictDataMapper dictDataMapper;

    // ====================== 字典类型 ======================

    /** 字典类型分页（可选 keyword 匹配名称/编码、status 过滤） */
    public PageResult<SysDictTypeEntity> pageTypes(int page, int size, String keyword, Integer status) {
        Page<SysDictTypeEntity> param = new Page<>(page, size);
        LambdaQueryWrapper<SysDictTypeEntity> q = Wrappers.<SysDictTypeEntity>lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like(SysDictTypeEntity::getDictName, k).or().like(SysDictTypeEntity::getDictType, k));
        }
        q.eq(status != null, SysDictTypeEntity::getStatus, status);
        q.orderByAsc(SysDictTypeEntity::getId);
        IPage<SysDictTypeEntity> result = dictTypeMapper.selectPage(param, q);
        return toPageResult(result, page, size);
    }

    public SysDictTypeEntity getType(Long id) {
        SysDictTypeEntity e = dictTypeMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "字典类型不存在");
        }
        return e;
    }

    /** 校验 dict_type 编码唯一（写路径清空字典缓存，保证读路径即时一致） */
    @Transactional
    @CacheEvict(cacheNames = CACHE_DICT, allEntries = true)
    public SysDictTypeEntity createType(String dictName, String dictType, Integer status, String remark) {
        if (dictName == null || dictName.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典名称不能为空");
        }
        if (dictType == null || dictType.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典类型不能为空");
        }
        String type = dictType.trim();
        if (dictTypeMapper.selectCount(Wrappers.<SysDictTypeEntity>lambdaQuery()
                .eq(SysDictTypeEntity::getDictType, type)) > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典类型已存在：" + type);
        }
        SysDictTypeEntity e = new SysDictTypeEntity();
        e.setDictName(dictName.trim());
        e.setDictType(type);
        e.setStatus(status == null ? 1 : status);
        e.setRemark(remark);
        e.setIsDeleted(0);
        dictTypeMapper.insert(e);
        return e;
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_DICT, allEntries = true)
    public SysDictTypeEntity updateType(Long id, String dictName, String dictType, Integer status, String remark) {
        SysDictTypeEntity e = getType(id);
        if (dictName != null) {
            if (dictName.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "字典名称不能为空");
            }
            e.setDictName(dictName.trim());
        }
        if (dictType != null) {
            if (dictType.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "字典类型不能为空");
            }
            String type = dictType.trim();
            if (!type.equals(e.getDictType())
                    && dictTypeMapper.selectCount(Wrappers.<SysDictTypeEntity>lambdaQuery()
                    .eq(SysDictTypeEntity::getDictType, type)
                    .ne(SysDictTypeEntity::getId, id)) > 0) {
                throw new BizException(ResultCode.PARAM_ERROR, "字典类型已存在：" + type);
            }
            e.setDictType(type);
        }
        if (status != null) e.setStatus(status);
        if (remark != null) e.setRemark(remark);
        dictTypeMapper.updateById(e);
        return e;
    }

    /** 删除字典类型（联动删除其下字典数据，写路径清空字典缓存） */
    @Transactional
    @CacheEvict(cacheNames = CACHE_DICT, allEntries = true)
    public void deleteType(Long id) {
        SysDictTypeEntity e = getType(id);
        dictDataMapper.delete(Wrappers.<SysDictDataEntity>lambdaQuery()
                .eq(SysDictDataEntity::getDictType, e.getDictType()));
        dictTypeMapper.deleteById(id);
    }

    // ====================== 字典数据 ======================

    /**
     * 按类型取启用状态的字典数据（下拉框等场景），走 Redis 缓存。
     * 注意：本方法可能被本类内部调用方绕过代理，请从 Controller 层直接调用以命中缓存。
     */
    @Cacheable(cacheNames = CACHE_DICT, key = "'data:' + #dictType", unless = "#result == null || #result.isEmpty()")
    public List<SysDictDataEntity> listDataByType(String dictType) {
        return dictDataMapper.selectList(Wrappers.<SysDictDataEntity>lambdaQuery()
                .eq(SysDictDataEntity::getDictType, dictType)
                .eq(SysDictDataEntity::getStatus, 1)
                .orderByAsc(SysDictDataEntity::getDictSort)
                .orderByAsc(SysDictDataEntity::getId));
    }

    /** 字典数据分页（可选 dictType / status 过滤） */
    public PageResult<SysDictDataEntity> pageData(int page, int size, String dictType, Integer status) {
        Page<SysDictDataEntity> param = new Page<>(page, size);
        LambdaQueryWrapper<SysDictDataEntity> q = Wrappers.<SysDictDataEntity>lambdaQuery();
        if (dictType != null && !dictType.isBlank()) {
            q.eq(SysDictDataEntity::getDictType, dictType.trim());
        }
        q.eq(status != null, SysDictDataEntity::getStatus, status);
        q.orderByAsc(SysDictDataEntity::getDictSort);
        IPage<SysDictDataEntity> result = dictDataMapper.selectPage(param, q);
        return toPageResult(result, page, size);
    }

    public SysDictDataEntity getData(Long id) {
        SysDictDataEntity e = dictDataMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "字典数据不存在");
        }
        return e;
    }

    /** 新增字典数据（写路径清空字典缓存，保证按类型读缓存即时一致） */
    @Transactional
    @CacheEvict(cacheNames = CACHE_DICT, allEntries = true)
    public SysDictDataEntity createData(String dictType, String dictLabel, String dictValue,
                                        Integer dictSort, Integer isDefault, Integer status, String remark) {
        if (dictType == null || dictType.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典类型不能为空");
        }
        if (dictLabel == null || dictLabel.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典标签不能为空");
        }
        if (dictValue == null || dictValue.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典键值不能为空");
        }
        String type = dictType.trim();
        if (dictTypeMapper.selectCount(Wrappers.<SysDictTypeEntity>lambdaQuery()
                .eq(SysDictTypeEntity::getDictType, type)) == 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "字典类型不存在：" + type);
        }
        SysDictDataEntity e = new SysDictDataEntity();
        e.setDictType(type);
        e.setDictLabel(dictLabel.trim());
        e.setDictValue(dictValue.trim());
        e.setDictSort(dictSort == null ? 0 : dictSort);
        e.setIsDefault(isDefault == null ? 0 : isDefault);
        e.setStatus(status == null ? 1 : status);
        e.setRemark(remark);
        dictDataMapper.insert(e);
        return e;
    }

    /** 修改字典数据（写路径清空字典缓存） */
    @Transactional
    @CacheEvict(cacheNames = CACHE_DICT, allEntries = true)
    public SysDictDataEntity updateData(Long id, String dictLabel, String dictValue,
                                        Integer dictSort, Integer isDefault, Integer status, String remark) {
        SysDictDataEntity e = getData(id);
        if (dictLabel != null) {
            if (dictLabel.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "字典标签不能为空");
            }
            e.setDictLabel(dictLabel.trim());
        }
        if (dictValue != null) {
            if (dictValue.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "字典键值不能为空");
            }
            e.setDictValue(dictValue.trim());
        }
        if (dictSort != null) e.setDictSort(dictSort);
        if (isDefault != null) e.setIsDefault(isDefault);
        if (status != null) e.setStatus(status);
        if (remark != null) e.setRemark(remark);
        dictDataMapper.updateById(e);
        return e;
    }

    /** 删除字典数据（写路径清空字典缓存） */
    @Transactional
    @CacheEvict(cacheNames = CACHE_DICT, allEntries = true)
    public void deleteData(Long id) {
        getData(id);
        dictDataMapper.deleteById(id);
    }

    // ====================== 缓存刷新 ======================

    /** 刷新字典缓存：清空 system:dict 全部缓存条目，下次读取回源重建 */
    @CacheEvict(cacheNames = CACHE_DICT, allEntries = true)
    public void refreshCache() {
        // 仅清缓存，无需其它动作
    }

    // ====================== 工具 ======================

    private <T> PageResult<T> toPageResult(IPage<T> result, int page, int size) {
        PageResult<T> pr = new PageResult<>();
        pr.setTotal(result.getTotal());
        pr.setPage(page);
        pr.setSize(size);
        pr.setRecords(result.getRecords());
        return pr;
    }
}
