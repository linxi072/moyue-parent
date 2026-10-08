package com.moyue.search.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.search.domain.dto.query.BlockWordQuery;
import com.moyue.search.domain.entity.BlockWord;
import com.moyue.search.domain.vo.BlockWordVO;

/**
 * 搜索屏蔽词服务：CRUD + 启用 / 停用。
 *
 * @author moyue
 */
public interface BlockWordService {

    PageResult<BlockWordVO> pageBlockWords(BlockWordQuery query);

    Long createBlockWord(BlockWord entity);

    boolean updateBlockWord(BlockWord entity);

    boolean deleteBlockWord(Long id);

    /** 启用 */
    boolean enable(Long id);

    /** 停用 */
    boolean disable(Long id);
}
