package com.moyue.commerce.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.commerce.domain.entity.PointsAccount;
import com.moyue.commerce.domain.entity.PointsLog;
import com.moyue.commerce.mapper.PointsAccountMapper;
import com.moyue.commerce.mapper.PointsLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 积分账户服务单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：用户/金额校验、余额原子增减、账户自动开户、收入/消费累计、余额不足拒付（PAY_FAILED）。
 */
@ExtendWith(MockitoExtension.class)
class PointsServiceImplTest {

    @Mock
    private PointsAccountMapper accountMapper;

    @Mock
    private PointsLogMapper logMapper;

    @InjectMocks
    private PointsServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "init");
        TableInfoHelper.initTableInfo(assistant, PointsAccount.class);
        TableInfoHelper.initTableInfo(assistant, PointsLog.class);
    }

    private static PointsAccount accountWith(BigDecimal balance) {
        PointsAccount a = new PointsAccount();
        a.setId(1L);
        a.setBalance(balance);
        a.setTotalIncome(BigDecimal.ZERO);
        a.setTotalConsume(BigDecimal.ZERO);
        a.setFrozen(BigDecimal.ZERO);
        return a;
    }

    @Test
    void changePoints_nullUser_throwsParamError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePoints(null, 1, new BigDecimal("10"), "ref"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void changePoints_zeroAmount_throwsParamError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePoints(1L, 1, BigDecimal.ZERO, "ref"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void changePoints_earn_createsAccountAndAddsBalance() {
        when(accountMapper.selectOne(any())).thenReturn(null);
        when(accountMapper.insert(any(PointsAccount.class))).thenReturn(1);
        when(accountMapper.selectById(any())).thenReturn(accountWith(BigDecimal.ZERO));
        when(accountMapper.updateById(any(PointsAccount.class))).thenReturn(1);
        when(logMapper.insert(any(PointsLog.class))).thenReturn(1);

        BigDecimal after = service.changePoints(9L, 1, new BigDecimal("100"), "ref");

        assertEquals(new BigDecimal("100"), after);
        verify(accountMapper).insert(any(PointsAccount.class));
        verify(accountMapper).updateById(any(PointsAccount.class));
        verify(logMapper).insert(any(PointsLog.class));
    }

    @Test
    void changePoints_spend_updatesBalance() {
        when(accountMapper.selectOne(any())).thenReturn(accountWith(new BigDecimal("200")));
        when(accountMapper.updateById(any(PointsAccount.class))).thenReturn(1);
        when(logMapper.insert(any(PointsLog.class))).thenReturn(1);

        BigDecimal after = service.changePoints(9L, 2, new BigDecimal("-50"), "ref");

        assertEquals(new BigDecimal("150"), after);
        ArgumentCaptor<PointsAccount> cap = ArgumentCaptor.forClass(PointsAccount.class);
        verify(accountMapper).updateById(cap.capture());
        assertEquals(new BigDecimal("150"), cap.getValue().getBalance());
    }

    @Test
    void changePoints_insufficient_throwsPayFailed() {
        when(accountMapper.selectOne(any())).thenReturn(accountWith(new BigDecimal("30")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePoints(9L, 2, new BigDecimal("-50"), "ref"));
        assertEquals(ErrorCode.PAY_FAILED.getCode(), ex.getCode());
        verify(accountMapper, never()).updateById(any(PointsAccount.class));
    }
}
