package com.moyue.api.system.client;

import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.operation.service.AuthorIncomeService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 平台运营域稿酬流水进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-system) 已移除 OpenFeign。monolith 中并不存在单独的 AuthorIncomeService 之外的写方，
 * 故本适配器注入 {@link AuthorIncomeService}（operation 域查询服务）委托调用，
 * 实体→契约 DTO 的转换由该 Service 统一承载，保持契约一致。
 *
 * <p>P2-H 收尾：消除 api 包直接注入 operation 域 {@code AuthorIncomeMapper} 的表级耦合，
 * 回归「注入 @Service」范式（与 {@link SettlementClient} 一致）。</p>
 */
@Component
public class IncomeClient {

    private final AuthorIncomeService authorIncomeService;

    public IncomeClient(AuthorIncomeService authorIncomeService) {
        this.authorIncomeService = authorIncomeService;
    }

    /** 按作者查询稿酬流水（按结算月份倒序） */
    public R<List<AuthorIncomeDTO>> listByAuthor(Long authorId) {
        try {
            List<AuthorIncomeDTO> dtos = authorIncomeService.listByAuthor(authorId);
            return R.ok(dtos == null ? java.util.Collections.emptyList() : dtos);
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}
