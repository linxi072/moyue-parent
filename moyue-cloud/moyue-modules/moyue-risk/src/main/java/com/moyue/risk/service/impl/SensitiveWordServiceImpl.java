package com.moyue.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.risk.domain.dto.query.SensitiveWordQuery;
import com.moyue.risk.domain.entity.SensitiveWord;
import com.moyue.risk.domain.vo.SensitiveWordVO;
import com.moyue.risk.mapper.SensitiveWordMapper;
import com.moyue.risk.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 敏感词管理实现：CRUD + 启用 / 停用，唯一词校验。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private final SensitiveWordMapper sensitiveWordMapper;

    @Override
    public PageResult<SensitiveWordVO> pageWords(SensitiveWordQuery query) {
        var page = PageUtils.<SensitiveWord>page(query);
        var result = sensitiveWordMapper.selectPage(page, new LambdaQueryWrapper<SensitiveWord>()
                .like(StringUtils.hasText(query.getWord()), SensitiveWord::getWord, query.getWord())
                .eq(query.getLevel() != null, SensitiveWord::getLevel, query.getLevel())
                .eq(query.getEnabled() != null, SensitiveWord::getEnabled, query.getEnabled())
                .orderByDesc(SensitiveWord::getLevel)
                .orderByDesc(SensitiveWord::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWord(SensitiveWord entity) {
        if (!StringUtils.hasText(entity.getWord())) {
            throw new BusinessException(com.moyue.common.core.exception.ErrorCode.PARAM_ERROR, "敏感词不能为空");
        }
        if (entity.getLevel() == null) {
            entity.setLevel(SensitiveWord.LEVEL_BLOCK);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(SensitiveWord.ENABLED_ON);
        }
        if (entity.getHitCount() == null) {
            entity.setHitCount(0);
        }
        long dup = sensitiveWordMapper.selectCount(
                new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getWord, entity.getWord()));
        if (dup > 0) {
            throw new BusinessException(com.moyue.common.core.exception.ErrorCode.PARAM_ERROR, "敏感词已存在：" + entity.getWord());
        }
        sensitiveWordMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateWord(SensitiveWord entity) {
        SensitiveWord exist = sensitiveWordMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("敏感词");
        }
        return sensitiveWordMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWord(Long id) {
        SensitiveWord exist = sensitiveWordMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("敏感词");
        }
        return sensitiveWordMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean enable(Long id) {
        SensitiveWord upd = new SensitiveWord();
        upd.setId(id);
        upd.setEnabled(SensitiveWord.ENABLED_ON);
        return sensitiveWordMapper.updateById(upd) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disable(Long id) {
        SensitiveWord upd = new SensitiveWord();
        upd.setId(id);
        upd.setEnabled(SensitiveWord.ENABLED_OFF);
        return sensitiveWordMapper.updateById(upd) > 0;
    }

    private SensitiveWordVO toVO(SensitiveWord e) {
        return SensitiveWordVO.builder()
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
