package com.moyue.api.system.client;

import com.moyue.api.system.dto.SettlementDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 平台运营域（moyue-system）结算单 Feign 客户端。
 * 供 moyue-commerce 查询作者结算单，收敛「commerce 双映射 settlement_order 同表」技术债（见 P0-1 计划现状）。
 * 内部端点不走网关注入角色，仅经服务发现（lb://moyue-system）在集群内调用。
 */
@FeignClient(name = "moyue-system", fallbackFactory = SettlementClientFallbackFactory.class)
public interface SettlementClient {

    /** 按作者查询结算单列表：GET /api/v1/internal/settlements?authorId= */
    @GetMapping("/api/v1/internal/settlements")
    R<List<SettlementDTO>> listSettlements(@RequestParam("authorId") Long authorId);

    /** 按 ID 查询单张结算单：GET /api/v1/internal/settlements/{id} */
    @GetMapping("/api/v1/internal/settlements/{id}")
    R<SettlementDTO> getSettlement(@PathVariable("id") Long id);
}
