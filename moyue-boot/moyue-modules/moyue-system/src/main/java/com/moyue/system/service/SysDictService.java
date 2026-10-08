package com.moyue.system.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.dto.query.DictDataQuery;
import com.moyue.system.domain.dto.query.DictTypeQuery;
import com.moyue.system.domain.entity.SysDictData;
import com.moyue.system.domain.entity.SysDictType;
import com.moyue.system.domain.vo.DictDataVO;
import com.moyue.system.domain.vo.DictTypeVO;

import java.util.List;

/**
 * 字典管理域（⑤）服务：字典类型 + 字典数据。
 *
 * <p>缓存策略：字典数据按 dict_type 缓存在 Redis（见 CacheNames.DICT_DATA），
 * 变更时主动失效，避免「改了字典前台不生效」。
 *
 * @author moyue
 */
public interface SysDictService {

    PageResult<DictTypeVO> pageDictTypes(DictTypeQuery query);

    List<DictTypeVO> listDictTypes(DictTypeQuery query);

    Long createDictType(SysDictType entity);

    boolean updateDictType(SysDictType entity);

    /** 删除类型时级联删除其下字典数据 */
    boolean deleteDictType(Long dictId);

    PageResult<DictDataVO> pageDictData(DictDataQuery query);

    Long createDictData(SysDictData entity);

    boolean updateDictData(SysDictData entity);

    boolean deleteDictData(Long dictCode);

    /**
     * 按类型查字典数据（优先缓存）。
     *
     * @param dictType 字典类型
     * @return 字典数据列表
     */
    List<DictDataVO> listDataByType(String dictType);
}
