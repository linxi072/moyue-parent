package com.moyue.api.system.client;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.operation.entity.AuthorIncomeEntity;
import com.moyue.operation.mapper.AuthorIncomeMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 平台运营域稿酬流水进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-system) 已移除 OpenFeign。monolith 中并不存在单独的 AuthorIncomeService，
 * 原内部端点 {@code IncomeInternalController} 直接经 {@link AuthorIncomeMapper} 查询并转换，
 * 故本适配器注入同一 Mapper 复刻其查询与映射逻辑（保持契约一致），供 moyue-commerce 查询作者稿酬流水。
 *
 * <p>建议（非本次范围）：后续可抽出一个 AuthorIncomeService 承载该查询，使本适配器回归「注入 @Service」的范式。</p>
 */
@Component
public class IncomeClient {

    private final AuthorIncomeMapper authorIncomeMapper;

    public IncomeClient(AuthorIncomeMapper authorIncomeMapper) {
        this.authorIncomeMapper = authorIncomeMapper;
    }

    /** 按作者查询稿酬流水（按结算月份倒序） */
    public R<List<AuthorIncomeDTO>> listByAuthor(Long authorId) {
        try {
            List<AuthorIncomeEntity> list = authorIncomeMapper.selectList(Wrappers.<AuthorIncomeEntity>lambdaQuery()
                    .eq(AuthorIncomeEntity::getAuthorId, authorId)
                    .orderByDesc(AuthorIncomeEntity::getSettleMonth));
            List<AuthorIncomeDTO> dtos = list == null ? java.util.Collections.emptyList()
                    : list.stream().map(IncomeClient::toDto).collect(Collectors.toList());
            return R.ok(dtos);
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 实体 → 跨服务契约 DTO */
    private static AuthorIncomeDTO toDto(AuthorIncomeEntity e) {
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
