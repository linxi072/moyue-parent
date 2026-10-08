package com.moyue.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.search.domain.dto.query.SearchHotWordQuery;
import com.moyue.search.domain.entity.SearchHotWord;
import com.moyue.search.domain.vo.SearchHotWordVO;
import com.moyue.search.mapper.SearchHotWordMapper;
import com.moyue.search.service.SearchHotWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 搜索域（热词）实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchHotWordServiceImpl implements SearchHotWordService {

    private final SearchHotWordMapper hotWordMapper;

    @Override
    public PageResult<SearchHotWordVO> pageHotWords(SearchHotWordQuery query) {
        var page = PageUtils.<SearchHotWord>page(query);
        var result = hotWordMapper.selectPage(page, new LambdaQueryWrapper<SearchHotWord>()
                .like(StringUtils.hasText(query.getWord()), SearchHotWord::getWord, query.getWord())
                .eq(query.getEnabled() != null, SearchHotWord::getEnabled, query.getEnabled())
                .orderByDesc(SearchHotWord::getWeight));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createHotWord(SearchHotWord entity) {
        if (!StringUtils.hasText(entity.getWord())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "热词不能为空");
        }
        if (entity.getWeight() == null) {
            entity.setWeight(0);
        }
        if (entity.getHitCount() == null) {
            entity.setHitCount(0);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        hotWordMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateHotWord(SearchHotWord entity) {
        SearchHotWord exist = hotWordMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("搜索热词");
        }
        return hotWordMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteHotWord(Long id) {
        SearchHotWord exist = hotWordMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("搜索热词");
        }
        return hotWordMapper.deleteById(id) > 0;
    }

    @Override
    public List<SearchHotWordVO> top(int limit) {
        int n = Math.max(1, Math.min(limit, 50));
        // 权重相同时按命中次数、再按 id 兜底，保证榜单位次稳定可复现（否则并列项次序由 DB 决定）
        var list = hotWordMapper.selectList(new LambdaQueryWrapper<SearchHotWord>()
                .eq(SearchHotWord::getEnabled, 1)
                .orderByDesc(SearchHotWord::getWeight)
                .orderByDesc(SearchHotWord::getHitCount)
                .orderByAsc(SearchHotWord::getId)
                .last("LIMIT " + n));
        return list.stream().map(this::toVO).toList();
    }

    @Override
    public List<SearchHotWordVO> suggest(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        // MySQL 前缀 LIKE 降级（真实场景应走 ES completion suggester，沙箱无 ES）
        var list = hotWordMapper.selectList(new LambdaQueryWrapper<SearchHotWord>()
                .likeRight(SearchHotWord::getWord, keyword.trim())
                .eq(SearchHotWord::getEnabled, 1)
                .orderByDesc(SearchHotWord::getWeight)
                .last("LIMIT 10"));
        return list.stream().map(this::toVO).toList();
    }

    private SearchHotWordVO toVO(SearchHotWord e) {
        return SearchHotWordVO.builder()
                .id(e.getId())
                .word(e.getWord())
                .hitCount(e.getHitCount())
                .weight(e.getWeight())
                .enabled(e.getEnabled())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}
