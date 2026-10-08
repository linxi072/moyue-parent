package com.moyue.risk.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.risk.domain.dto.query.SensitiveWordQuery;
import com.moyue.risk.domain.entity.SensitiveWord;
import com.moyue.risk.domain.vo.SensitiveWordVO;

/**
 * 敏感词管理（运营端）：CRUD + 启用 / 停用。
 *
 * @author moyue
 */
public interface SensitiveWordService {

    PageResult<SensitiveWordVO> pageWords(SensitiveWordQuery query);

    Long createWord(SensitiveWord entity);

    boolean updateWord(SensitiveWord entity);

    boolean deleteWord(Long id);

    boolean enable(Long id);

    boolean disable(Long id);
}
