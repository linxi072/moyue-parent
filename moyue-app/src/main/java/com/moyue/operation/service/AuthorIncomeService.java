package com.moyue.operation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.operation.entity.AuthorIncomeEntity;
import com.moyue.operation.mapper.AuthorIncomeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 作者稿酬流水领域查询服务（P2-H 收尾）。
 *
 * <p>「{@code author_income} 共享表」技术债收敛收口点：该表的<strong>唯一写方</strong>仍是
 * {@link RewardService}（打赏分成）与 {@code SettlementService}（买断/结算），本服务<strong>只承载查询</strong>，
 * 绝不直接写 {@code author_income}（不回退 P0 已落地的稿酬打款闭环）。</p>
 *
 * <p>跨域读取统一经本 Service 而非裸 {@code AuthorIncomeMapper}：{@code IncomeClient}（com.moyue.api.system.client）
 * 与 {@code IncomeInternalController} 均注入本 Service，使跨域客户端回归「注入 @Service」范式，
 * 消除 api 包直接耦合 operation 域表级 Mapper 的架构 smell（对齐 {@code SettlementClient} 写法）。</p>
 */
@Service
public class AuthorIncomeService {

    @Autowired
    private AuthorIncomeMapper authorIncomeMapper;

    /** 按作者查询稿酬流水（按结算月份倒序），返回跨服务契约 DTO */
    public List<AuthorIncomeDTO> listByAuthor(Long authorId) {
        if (authorId == null) {
            return Collections.emptyList();
        }
        List<AuthorIncomeEntity> list = authorIncomeMapper.selectList(Wrappers.<AuthorIncomeEntity>lambdaQuery()
                .eq(AuthorIncomeEntity::getAuthorId, authorId)
                .orderByDesc(AuthorIncomeEntity::getSettleMonth));
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(AuthorIncomeService::toDto).collect(Collectors.toList());
    }

    /** 实体 → 跨服务契约 DTO（与 AuthorIncomeDTO 字段对齐） */
    public static AuthorIncomeDTO toDto(AuthorIncomeEntity e) {
        if (e == null) {
            return null;
        }
        AuthorIncomeDTO d = new AuthorIncomeDTO();
        d.setId(e.getId());
        d.setAuthorId(e.getAuthorId());
        d.setBookId(e.getBookId());
        d.setOrderNo(e.getOrderNo());
        d.setIncomeType(e.getIncomeType());
        d.setAmount(e.getAmount());
        d.setSettleMonth(e.getSettleMonth());
        d.setSettlementId(e.getSettlementId());
        d.setCreateTime(e.getCreateTime());
        return d;
    }
}
