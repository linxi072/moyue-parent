package com.moyue.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.ai.domain.dto.query.AiQuotaQuery;
import com.moyue.ai.domain.entity.AiQuota;
import com.moyue.ai.domain.vo.AiQuotaVO;
import com.moyue.ai.mapper.AiQuotaMapper;
import com.moyue.ai.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 配额实现：列表 / 重置 / 扣减（不足抛异常）。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    /** 新用户默认配额（token） */
    private static final int DEFAULT_TOTAL = 1000;

    private final AiQuotaMapper quotaMapper;

    @Override
    public PageResult<AiQuotaVO> pageQuotas(AiQuotaQuery query) {
        var page = PageUtils.<AiQuota>page(query);
        var result = quotaMapper.selectPage(page, new LambdaQueryWrapper<AiQuota>()
                .eq(query.getUserId() != null, AiQuota::getUserId, query.getUserId())
                .orderByDesc(AiQuota::getRemain)
                .orderByDesc(AiQuota::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiQuota getOrCreate(Long userId) {
        Long uid = userId == null ? 0L : userId;
        AiQuota exist = quotaMapper.selectOne(new LambdaQueryWrapper<AiQuota>().eq(AiQuota::getUserId, uid));
        if (exist != null) {
            return exist;
        }
        AiQuota q = new AiQuota();
        q.setUserId(uid);
        q.setTotal(DEFAULT_TOTAL);
        q.setUsed(0);
        q.setRemain(DEFAULT_TOTAL);
        quotaMapper.insert(q);
        return q;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deduct(Long userId, int amount) {
        Long uid = userId == null ? 0L : userId;
        AiQuota q = quotaMapper.selectOne(new LambdaQueryWrapper<AiQuota>().eq(AiQuota::getUserId, uid));
        if (q == null) {
            q = new AiQuota();
            q.setUserId(uid);
            q.setTotal(DEFAULT_TOTAL);
            q.setUsed(0);
            q.setRemain(DEFAULT_TOTAL);
            quotaMapper.insert(q);
        }
        if (q.getRemain() == null || q.getRemain() < amount) {
            throw new BusinessException(ErrorCode.PAY_FAILED, "AI 配额不足，剩余 " + (q.getRemain() == null ? 0 : q.getRemain()) + " token");
        }
        quotaMapper.update(null, new LambdaUpdateWrapper<AiQuota>()
                .eq(AiQuota::getUserId, uid)
                .setSql("used = used + " + amount)
                .setSql("remain = remain - " + amount));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reset(Long userId, int total) {
        Long uid = userId == null ? 0L : userId;
        AiQuota q = quotaMapper.selectOne(new LambdaQueryWrapper<AiQuota>().eq(AiQuota::getUserId, uid));
        if (q == null) {
            q = new AiQuota();
            q.setUserId(uid);
        }
        q.setTotal(total);
        q.setUsed(0);
        q.setRemain(total);
        if (q.getId() == null) {
            quotaMapper.insert(q);
        } else {
            quotaMapper.updateById(q);
        }
    }

    private AiQuotaVO toVO(AiQuota e) {
        return AiQuotaVO.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .total(e.getTotal())
                .used(e.getUsed())
                .remain(e.getRemain())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}
