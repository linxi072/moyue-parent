package com.moyue.api.system.client;

import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 平台运营域（moyue-system）稿酬流水 Feign 客户端。
 * 供 moyue-commerce 查询作者稿酬流水，收敛「commerce 双映射 author_income 同表」技术债（见 P2-H 计划）。
 * 内部端点不走网关注入角色，仅经服务发现（lb://moyue-system）在集群内调用；网关不暴露 /api/v1/internal/**。
 */
@FeignClient(name = "moyue-system", contextId = "moyue-system-income", fallbackFactory = IncomeClientFallbackFactory.class)
public interface IncomeClient {

    /** 按作者查询稿酬流水：GET /api/v1/internal/income?authorId= */
    @GetMapping("/api/v1/internal/income")
    R<List<AuthorIncomeDTO>> listByAuthor(@RequestParam("authorId") Long authorId);
}
