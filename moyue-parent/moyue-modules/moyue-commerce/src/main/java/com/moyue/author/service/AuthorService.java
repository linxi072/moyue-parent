package com.moyue.author.service;

import com.moyue.api.system.client.IncomeClient;
import com.moyue.api.system.client.SettlementClient;
import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.api.system.dto.SettlementDTO;
import com.moyue.common.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 作者稿酬业务：按作者查询稿酬流水，并经由 Feign（SettlementClient）查询平台运营域结算单。
 *
 * <p>结算单主属主在 moyue-system（settlement_order 表由其持有），commerce 不再双映射同表，
 * 而是通过 {@link SettlementClient} 收敛「共享表技术债」（见 P0-1 计划）。</p>
 */
@Service
public class AuthorService {

    /** 稿酬流水 Feign 客户端（目标 moyue-system）；不可用时稿酬流水查询降级为空，不阻断作者主页主流程 */
    @Autowired(required = false)
    private IncomeClient incomeClient;

    /** 结算单 Feign 客户端（目标 moyue-system）；不可用时结算单查询降级为空，不阻断稿酬流水主流程 */
    @Autowired(required = false)
    private SettlementClient settlementClient;

    /** 按 author_id 查询该作者的全部稿酬流水（经 Feign 到 moyue-system，按结算月份倒序） */
    public List<AuthorIncomeDTO> listByAuthor(Long authorId) {
        if (incomeClient == null) {
            return Collections.emptyList();
        }
        try {
            com.moyue.common.R<List<AuthorIncomeDTO>> resp = incomeClient.listByAuthor(authorId);
            if (resp != null && resp.getCode() == ResultCode.SUCCESS.getCode() && resp.getData() != null) {
                return resp.getData();
            }
            return Collections.emptyList();
        } catch (Exception ex) {
            // 降级：稿酬流水查询失败不影响作者主页主流程
            return Collections.emptyList();
        }
    }

    /** 查询该作者的结算单列表（经 Feign 到 moyue-system；降级为空集合） */
    public List<SettlementDTO> listSettlements(Long userId) {
        if (settlementClient == null) {
            return Collections.emptyList();
        }
        try {
            com.moyue.common.R<List<SettlementDTO>> resp = settlementClient.listSettlements(userId);
            if (resp != null && resp.getCode() == ResultCode.SUCCESS.getCode() && resp.getData() != null) {
                return resp.getData();
            }
            return Collections.emptyList();
        } catch (Exception ex) {
            // 降级：结算单查询失败不影响稿酬流水主流程
            return Collections.emptyList();
        }
    }

    /** 查询单张结算单（经 Feign 到 moyue-system；降级为 null） */
    public SettlementDTO getSettlement(Long id) {
        if (settlementClient == null) {
            return null;
        }
        try {
            com.moyue.common.R<SettlementDTO> resp = settlementClient.getSettlement(id);
            if (resp != null && resp.getCode() == ResultCode.SUCCESS.getCode() && resp.getData() != null) {
                return resp.getData();
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
