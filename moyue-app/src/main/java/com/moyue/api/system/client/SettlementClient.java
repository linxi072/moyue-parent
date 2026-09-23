package com.moyue.api.system.client;

import com.moyue.api.system.dto.SettlementDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.operation.entity.SettlementOrderEntity;
import com.moyue.operation.service.SettlementService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 平台运营域结算单进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-system) 已移除 OpenFeign，改为直接注入 {@link SettlementService} 委托调用，
 * 实体→契约 DTO 的转换复刻自 {@code SettlementController} 的内部端点逻辑，保持契约一致。
 *
 * <p>未委托到 AuthorService（其 listSettlements/getSettlement 内部又回注本 Client，会形成循环依赖）。</p>
 */
@Component
public class SettlementClient {

    private final SettlementService settlementService;

    public SettlementClient(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /** 按作者查询结算单列表 */
    public R<List<SettlementDTO>> listSettlements(Long authorId) {
        try {
            List<SettlementOrderEntity> list = settlementService.listByAuthor(authorId);
            List<SettlementDTO> dtos = list == null ? new ArrayList<>()
                    : list.stream().map(SettlementClient::toDto).collect(Collectors.toList());
            return R.ok(dtos);
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 按 ID 查询单张结算单 */
    public R<SettlementDTO> getSettlement(Long id) {
        try {
            return R.ok(toDto(settlementService.getById(id)));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 实体 → 跨服务契约 DTO */
    private static SettlementDTO toDto(SettlementOrderEntity e) {
        if (e == null) {
            return null;
        }
        SettlementDTO dto = new SettlementDTO();
        dto.setId(e.getId());
        dto.setAuthorId(e.getAuthorId());
        dto.setPeriod(e.getPeriod());
        dto.setTotalAmount(e.getTotalAmount());
        dto.setStatus(e.getStatus());
        dto.setPayChannel(resolvePayChannel(e.getPayChannel()));
        dto.setPaySerial(e.getPaySerial());
        dto.setRemark(e.getRemark());
        dto.setOperatorId(e.getOperatorId());
        dto.setCreateTime(e.getCreateTime());
        dto.setUpdateTime(e.getUpdateTime());
        return dto;
    }

    /** 持久化渠道枚举（1 微信 / 2 支付宝）→ 契约层渠道标识 */
    private static String resolvePayChannel(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 1 -> "wechat";
            case 2 -> "alipay";
            default -> "stub";
        };
    }
}
