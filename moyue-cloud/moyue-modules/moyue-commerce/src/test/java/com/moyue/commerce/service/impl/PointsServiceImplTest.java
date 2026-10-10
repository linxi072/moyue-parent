package com.moyue.commerce.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.commerce.domain.dto.query.PointsLogQuery;
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
 * <p>覆盖：用户/金额校验、余额原子增减、账户自动开户、收入/消费累计、余额不足拒付（PAY_FAILED），
 * 以及 C 端积分钱包的作用域限定与签到幂等（myBalance / pageMyLogs / sign）。
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

    // ---------------------------------------------------------------- C 端积分钱包（G-L′）

    @Test
    void myBalance_noAccount_returnsZero() {
        when(accountMapper.selectOne(any())).thenReturn(null);
        assertEquals(BigDecimal.ZERO, service.myBalance(9L));
    }

    @Test
    void myBalance_existing_returnsBalance() {
        when(accountMapper.selectOne(any())).thenReturn(accountWith(new BigDecimal("88")));
        assertEquals(new BigDecimal("88"), service.myBalance(9L));
    }

    @Test
    void pageMyLogs_forcesScopeToUser() {
        when(logMapper.selectPage(any(), any())).thenReturn(new Page<PointsLog>());
        PointsLogQuery query = new PointsLogQuery();
        query.setUserId(999L); // 调用方伪造他人 userId，应被覆盖

        service.pageMyLogs(42L, query);

        ArgumentCaptor<LambdaQueryWrapper<PointsLog>> wcap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(logMapper).selectPage(any(), wcap.capture());
        assertTrue(wcap.getValue().getCustomSqlSegment().contains("user_id"),
                "C 端流水查询必须按 userId 限定作用域");
    }

    @Test
    void sign_alreadySigned_returnsBalanceWithoutGranting() {
        PointsLog todaySign = new PointsLog();
        todaySign.setBizType(PointsLog.BIZ_SIGN);
        when(logMapper.selectOne(any())).thenReturn(todaySign);
        when(accountMapper.selectOne(any())).thenReturn(accountWith(new BigDecimal("100")));

        BigDecimal after = service.sign(42L);

        assertEquals(new BigDecimal("100"), after);
        // 幂等：今日已签到不重复发放，不写账户、不写流水
        verify(accountMapper, never()).updateById(any(PointsAccount.class));
        verify(logMapper, never()).insert(any(PointsLog.class));
    }

    @Test
    void sign_firstTime_grantsPoints() {
        when(logMapper.selectOne(any())).thenReturn(null); // 今日未签到
        when(accountMapper.selectOne(any())).thenReturn(accountWith(new BigDecimal("100")));
        when(accountMapper.updateById(any(PointsAccount.class))).thenReturn(1);
        when(logMapper.insert(any(PointsLog.class))).thenReturn(1);

        BigDecimal after = service.sign(42L);

        assertEquals(new BigDecimal("110"), after);
        ArgumentCaptor<PointsLog> cap = ArgumentCaptor.forClass(PointsLog.class);
        verify(logMapper).insert(cap.capture());
        assertEquals(PointsLog.BIZ_SIGN, cap.getValue().getBizType());
        assertEquals(0, cap.getValue().getChangeAmount().compareTo(BigDecimal.TEN));
    }
}
