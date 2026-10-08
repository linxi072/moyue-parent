package com.moyue.commerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.commerce.domain.dto.query.PointsAccountQuery;
import com.moyue.commerce.domain.dto.query.PointsLogQuery;
import com.moyue.commerce.domain.entity.PointsAccount;
import com.moyue.commerce.domain.entity.PointsLog;
import com.moyue.commerce.domain.vo.PointsAccountVO;
import com.moyue.commerce.domain.vo.PointsLogVO;
import com.moyue.commerce.mapper.PointsAccountMapper;
import com.moyue.commerce.mapper.PointsLogMapper;
import com.moyue.commerce.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 积分账户服务实现：余额原子增减，不足即拒。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PointsAccountMapper accountMapper;
    private final PointsLogMapper logMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal changePoints(Long userId, int bizType, BigDecimal amount, String refId) {
        return changePoints(userId, bizType, amount, refId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal changePoints(Long userId, int bizType, BigDecimal amount, String refId, String remark) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户 ID 不能为空");
        }
        if (amount == null || amount.compareTo(ZERO) == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "积分变动金额不能为 0");
        }

        PointsAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<PointsAccount>()
                .eq(PointsAccount::getUserId, userId));
        if (acc == null) {
            acc = new PointsAccount();
            acc.setUserId(userId);
            acc.setBalance(ZERO);
            acc.setTotalIncome(ZERO);
            acc.setTotalConsume(ZERO);
            acc.setFrozen(ZERO);
            accountMapper.insert(acc);
            acc = accountMapper.selectById(acc.getId());
        }

        BigDecimal before = acc.getBalance() == null ? ZERO : acc.getBalance();
        BigDecimal after = before.add(amount);
        if (after.compareTo(ZERO) < 0) {
            throw new BusinessException(ErrorCode.PAY_FAILED, "积分余额不足，无法完成本次操作");
        }

        acc.setBalance(after);
        if (amount.compareTo(ZERO) > 0) {
            BigDecimal income = acc.getTotalIncome() == null ? ZERO : acc.getTotalIncome();
            acc.setTotalIncome(income.add(amount));
        } else {
            BigDecimal consume = acc.getTotalConsume() == null ? ZERO : acc.getTotalConsume();
            acc.setTotalConsume(consume.add(amount.negate()));
        }
        accountMapper.updateById(acc);

        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setBizType(bizType);
        log.setChangeAmount(amount);
        log.setBalanceAfter(after);
        log.setRefId(refId);
        log.setRemark(remark);
        logMapper.insert(log);
        return after;
    }

    @Override
    public PageResult<PointsAccountVO> pageAccounts(PointsAccountQuery query) {
        var page = PageUtils.<PointsAccount>page(query);
        var result = accountMapper.selectPage(page, new LambdaQueryWrapper<PointsAccount>()
                .eq(query.getUserId() != null, PointsAccount::getUserId, query.getUserId())
                .orderByDesc(PointsAccount::getCreateTime));
        return PageUtils.toResult(result, this::toAccountVO);
    }

    @Override
    public PageResult<PointsLogVO> pageLogs(PointsLogQuery query) {
        var page = PageUtils.<PointsLog>page(query);
        var result = logMapper.selectPage(page, new LambdaQueryWrapper<PointsLog>()
                .eq(query.getUserId() != null, PointsLog::getUserId, query.getUserId())
                .eq(query.getBizType() != null, PointsLog::getBizType, query.getBizType())
                .orderByDesc(PointsLog::getCreateTime));
        return PageUtils.toResult(result, this::toLogVO);
    }

    @Override
    public PointsAccountVO getAccount(Long userId) {
        PointsAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<PointsAccount>()
                .eq(PointsAccount::getUserId, userId));
        return acc == null ? null : toAccountVO(acc);
    }

    private PointsAccountVO toAccountVO(PointsAccount e) {
        return PointsAccountVO.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .balance(e.getBalance())
                .totalIncome(e.getTotalIncome())
                .totalConsume(e.getTotalConsume())
                .frozen(e.getFrozen())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }

    private PointsLogVO toLogVO(PointsLog e) {
        return PointsLogVO.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .bizType(e.getBizType())
                .changeAmount(e.getChangeAmount())
                .balanceAfter(e.getBalanceAfter())
                .refId(e.getRefId())
                .remark(e.getRemark())
                .createTime(e.getCreateTime())
                .build();
    }
}
