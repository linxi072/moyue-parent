package com.moyue.operation.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.common.R;
import com.moyue.operation.entity.AuthorIncomeEntity;
import com.moyue.operation.mapper.AuthorIncomeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 稿酬流水内部端点（P2-H）。
 * 供 moyue-commerce 经 {@code IncomeClient} 查询作者稿酬流水，收敛「commerce 双映射 author_income 同表」技术债：
 * author_income 表唯一写方为 moyue-system，commerce 不再直读，仅经本内部端点读取。
 * 路径 /api/v1/internal/income 不经网关暴露（由 InternalAuthInterceptor 校验 X-Service-Token）。
 */
@RestController
@RequestMapping("/api/v1")
public class IncomeInternalController {

    @Autowired
    private AuthorIncomeMapper authorIncomeMapper;

    /**
     * 按作者查询稿酬流水（按结算月份倒序）。
     * GET /api/v1/internal/income?authorId=
     */
    @GetMapping("/internal/income")
    public R<List<AuthorIncomeDTO>> listByAuthor(@RequestParam("authorId") Long authorId) {
        List<AuthorIncomeEntity> list = authorIncomeMapper.selectList(Wrappers.<AuthorIncomeEntity>lambdaQuery()
                .eq(AuthorIncomeEntity::getAuthorId, authorId)
                .orderByDesc(AuthorIncomeEntity::getSettleMonth));
        return R.ok(list.stream().map(IncomeInternalController::toDto).toList());
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
