package com.moyue.search.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.search.domain.dto.query.SearchHotWordQuery;
import com.moyue.search.domain.entity.SearchHotWord;
import com.moyue.search.domain.vo.SearchHotWordVO;

import java.util.List;

/**
 * 搜索域（热词）服务。
 *
 * @author moyue
 */
public interface SearchHotWordService {

    PageResult<SearchHotWordVO> pageHotWords(SearchHotWordQuery query);

    Long createHotWord(SearchHotWord entity);

    boolean updateHotWord(SearchHotWord entity);

    boolean deleteHotWord(Long id);

    /** 启用中的热词榜（按权重倒序取前 N） */
    List<SearchHotWordVO> top(int limit);

    /**
     * 搜索联想（前缀匹配，MySQL 降级，不接 ES）。
     *
     * @param keyword 输入前缀
     * @return 匹配的热词（启用中，按权重倒序）
     */
    List<SearchHotWordVO> suggest(String keyword);

    /**
     * C 端热词榜（公开，无用户归属）。
     *
     * <p>启用中热词按权重倒序，{@code limit} 收敛到 [1, 20]（比运营端更保守的展示上限）。
     */
    List<SearchHotWordVO> consumerHotWords(int limit);

    /**
     * C 端搜索联想（公开，无用户归属）。
     *
     * <p>空词直接返回空列表，不查库；否则委托 {@link #suggest(String)} 做前缀匹配。
     */
    List<SearchHotWordVO> consumerSuggest(String keyword);
}
