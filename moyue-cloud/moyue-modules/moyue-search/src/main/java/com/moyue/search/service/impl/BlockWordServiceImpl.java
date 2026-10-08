package com.moyue.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.search.domain.dto.query.BlockWordQuery;
import com.moyue.search.domain.entity.BlockWord;
import com.moyue.search.domain.vo.BlockWordVO;
import com.moyue.search.mapper.BlockWordMapper;
import com.moyue.search.service.BlockWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 搜索屏蔽词服务实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockWordServiceImpl implements BlockWordService {

    private final BlockWordMapper blockWordMapper;

    @Override
    public PageResult<BlockWordVO> pageBlockWords(BlockWordQuery query) {
        var page = PageUtils.<BlockWord>page(query);
        var result = blockWordMapper.selectPage(page, new LambdaQueryWrapper<BlockWord>()
                .like(StringUtils.hasText(query.getWord()), BlockWord::getWord, query.getWord())
                .eq(query.getLevel() != null, BlockWord::getLevel, query.getLevel())
                .eq(query.getEnabled() != null, BlockWord::getEnabled, query.getEnabled())
                .orderByDesc(BlockWord::getLevel)
                .orderByDesc(BlockWord::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBlockWord(BlockWord entity) {
        if (!StringUtils.hasText(entity.getWord())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "屏蔽词不能为空");
        }
        BlockWord exist = blockWordMapper.selectOne(new LambdaQueryWrapper<BlockWord>()
                .eq(BlockWord::getWord, entity.getWord()));
        if (exist != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "屏蔽词已存在：" + entity.getWord());
        }
        if (entity.getLevel() == null) {
            entity.setLevel(1);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        if (entity.getHitCount() == null) {
            entity.setHitCount(0);
        }
        blockWordMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBlockWord(BlockWord entity) {
        BlockWord exist = blockWordMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("屏蔽词");
        }
        return blockWordMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBlockWord(Long id) {
        BlockWord exist = blockWordMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("屏蔽词");
        }
        return blockWordMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean enable(Long id) {
        return setEnabled(id, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disable(Long id) {
        return setEnabled(id, 0);
    }

    private boolean setEnabled(Long id, int enabled) {
        BlockWord exist = blockWordMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("屏蔽词");
        }
        BlockWord upd = new BlockWord();
        upd.setId(id);
        upd.setEnabled(enabled);
        return blockWordMapper.updateById(upd) > 0;
    }

    private BlockWordVO toVO(BlockWord e) {
        return BlockWordVO.builder()
                .id(e.getId())
                .word(e.getWord())
                .level(e.getLevel())
                .enabled(e.getEnabled())
                .hitCount(e.getHitCount())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}
