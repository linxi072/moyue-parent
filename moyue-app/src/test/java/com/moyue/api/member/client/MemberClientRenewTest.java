package com.moyue.api.member.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 会员客户端续费接缝单测（P2-B）。验证 renewDueSubscriptions 委托 MemberService，
 * 且服务异常时降级 SERVICE_DEGRADED（与既有 getBenefits/syncExpired 同范式）。
 */
@ExtendWith(MockitoExtension.class)
class MemberClientRenewTest {

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberClient memberClient;

    @Test
    void renewDueSubscriptions_delegatesToService() {
        when(memberService.renewDueSubscriptions()).thenReturn(3);
        R<Integer> r = memberClient.renewDueSubscriptions();
        assertThat(r.getCode()).isZero();
        assertThat(r.getData()).isEqualTo(3);
    }

    @Test
    void renewDueSubscriptions_serviceThrows_degrades() {
        when(memberService.renewDueSubscriptions()).thenThrow(new RuntimeException("db down"));
        R<Integer> r = memberClient.renewDueSubscriptions();
        assertThat(r.getCode()).isEqualTo(ResultCode.SERVICE_DEGRADED.getCode());
    }
}
