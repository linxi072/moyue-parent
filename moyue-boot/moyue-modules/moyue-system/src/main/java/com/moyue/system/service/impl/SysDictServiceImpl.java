package com.moyue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.redis.constant.CacheNames;
import com.moyue.common.redis.service.RedisService;
import com.moyue.system.domain.dto.query.DictDataQuery;
import com.moyue.system.domain.dto.query.DictTypeQuery;
import com.moyue.system.domain.entity.SysDictData;
import com.moyue.system.domain.entity.SysDictType;
import com.moyue.system.domain.vo.DictDataVO;
import com.moyue.system.domain.vo.DictTypeVO;
import com.moyue.system.mapper.SysDictDataMapper;
import com.moyue.system.mapper.SysDictTypeMapper;
import com.moyue.system.service.SysDictService;
import com.moyue.system.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 字典管理域（⑤）实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictServiceImpl implements SysDictService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;
    private final RedisService redisService;

    private static String cacheKey(String dictType) {
        return CacheNames.DICT_DATA + ":" + dictType;
    }

    // ------------------------------------------------------ 字典类型

    @Override
    public PageResult<DictTypeVO> pageDictTypes(DictTypeQuery query) {
        var page = PageUtils.<SysDictType>page(query);
        var result = dictTypeMapper.selectPage(page, buildTypeWrapper(query));
        return PageUtils.toResult(result, this::toTypeVO);
    }

    @Override
    public List<DictTypeVO> listDictTypes(DictTypeQuery query) {
        return dictTypeMapper.selectList(buildTypeWrapper(query))
                .stream().map(this::toTypeVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDictType(SysDictType entity) {
        checkTypeUnique(entity.getDictType(), null);
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        dictTypeMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDictType(SysDictType entity) {
        SysDictType exist = dictTypeMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("字典类型");
        }
        checkTypeUnique(entity.getDictType(), entity.getId());
        // 类型标识变更时，级联更新其下数据并失效新旧缓存
        boolean typeChanged = StringUtils.hasText(entity.getDictType())
                && !entity.getDictType().equals(exist.getDictType());
        if (typeChanged) {
            SysDictData batch = new SysDictData();
            batch.setDictType(entity.getDictType());
            dictDataMapper.update(batch, new LambdaQueryWrapper<SysDictData>()
                    .eq(SysDictData::getDictType, exist.getDictType()));
            evict(exist.getDictType());
        }
        evict(entity.getDictType());
        return dictTypeMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDictType(Long dictId) {
        SysDictType exist = dictTypeMapper.selectById(dictId);
        if (exist == null) {
            throw BusinessException.notFound("字典类型");
        }
        dictDataMapper.delete(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, exist.getDictType()));
        evict(exist.getDictType());
        return dictTypeMapper.deleteById(dictId) > 0;
    }

    // ------------------------------------------------------ 字典数据

    @Override
    public PageResult<DictDataVO> pageDictData(DictDataQuery query) {
        var page = PageUtils.<SysDictData>page(query);
        var result = dictDataMapper.selectPage(page, buildDataWrapper(query));
        return PageUtils.toResult(result, this::toDataVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDictData(SysDictData entity) {
        if (entity.getDictSort() == null) {
            entity.setDictSort(0);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        dictDataMapper.insert(entity);
        evict(entity.getDictType());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDictData(SysDictData entity) {
        if (dictDataMapper.selectById(entity.getId()) == null) {
            throw BusinessException.notFound("字典数据");
        }
        boolean ok = dictDataMapper.updateById(entity) > 0;
        evict(entity.getDictType());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDictData(Long dictCode) {
        SysDictData exist = dictDataMapper.selectById(dictCode);
        if (exist == null) {
            throw BusinessException.notFound("字典数据");
        }
        evict(exist.getDictType());
        return dictDataMapper.deleteById(dictCode) > 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DictDataVO> listDataByType(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return List.of();
        }
        String key = cacheKey(dictType);
        try {
            Object cached = redisService.get(key);
            if (cached instanceof List<?> list) {
                return (List<DictDataVO>) list;
            }
        } catch (Exception e) {
            log.warn("字典缓存读取失败，回退数据库：{}", e.getMessage());
        }
        List<DictDataVO> list = dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDictType, dictType)
                        .eq(SysDictData::getStatus, 1)
                        .orderByAsc(SysDictData::getDictSort))
                .stream().map(this::toDataVO).collect(Collectors.toList());
        try {
            redisService.set(key, list);
        } catch (Exception e) {
            log.warn("字典缓存写入失败：{}", e.getMessage());
        }
        return list;
    }

    private void evict(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return;
        }
        try {
            redisService.delete(cacheKey(dictType));
        } catch (Exception e) {
            log.warn("字典缓存失效失败：{}", e.getMessage());
        }
    }

    private void checkTypeUnique(String dictType, Long excludeId) {
        if (!StringUtils.hasText(dictType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字典类型不能为空");
        }
        Long count = dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType)
                .ne(excludeId != null, SysDictType::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字典类型已存在：" + dictType);
        }
    }

    private LambdaQueryWrapper<SysDictType> buildTypeWrapper(DictTypeQuery query) {
        return new LambdaQueryWrapper<SysDictType>()
                .like(StringUtils.hasText(query.getDictName()), SysDictType::getDictName, query.getDictName())
                .like(StringUtils.hasText(query.getDictType()), SysDictType::getDictType, query.getDictType())
                .eq(query.getStatus() != null, SysDictType::getStatus, query.getStatus())
                .orderByAsc(SysDictType::getId);
    }

    private LambdaQueryWrapper<SysDictData> buildDataWrapper(DictDataQuery query) {
        return new LambdaQueryWrapper<SysDictData>()
                .eq(StringUtils.hasText(query.getDictType()), SysDictData::getDictType, query.getDictType())
                .like(StringUtils.hasText(query.getDictLabel()), SysDictData::getDictLabel, query.getDictLabel())
                .eq(query.getStatus() != null, SysDictData::getStatus, query.getStatus())
                .orderByAsc(SysDictData::getDictSort);
    }

    private DictTypeVO toTypeVO(SysDictType entity) {
        return DictTypeVO.builder()
                .id(entity.getId())
                .dictName(entity.getDictName())
                .dictType(entity.getDictType())
                .status(entity.getStatus())
                .createTime(entity.getCreateTime())
                .remark(entity.getRemark())
                .build();
    }

    private DictDataVO toDataVO(SysDictData entity) {
        return DictDataVO.builder()
                .id(entity.getId())
                .dictType(entity.getDictType())
                .dictLabel(entity.getDictLabel())
                .dictValue(entity.getDictValue())
                .dictSort(entity.getDictSort())
                .isDefault(entity.getIsDefault())
                .status(entity.getStatus())
                .createTime(entity.getCreateTime())
                .remark(entity.getRemark())
                .build();
    }
}
