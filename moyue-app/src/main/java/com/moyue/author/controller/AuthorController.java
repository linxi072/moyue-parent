package com.moyue.author.controller;

import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.api.system.dto.SettlementDTO;
import com.moyue.author.service.AuthorService;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者稿酬接口。
 * 路径前缀 /api/v1 与网关路由、Feign 客户端路径保持一致。
 * 结算单相关查询走 Feign（SettlementClient → moyue-system），不在 commerce 双映射 settlement_order 表。
 */
@RestController
@RequestMapping("/api/v1")
public class AuthorController {

    @Autowired
    private AuthorService authorService;

    /** 按作者查询稿酬流水：GET /api/v1/author/income/{authorId} */
    @GetMapping("/author/income/{authorId}")
    public R<List<AuthorIncomeDTO>> listIncome(@PathVariable Long authorId) {
        return R.ok(authorService.listByAuthor(authorId));
    }

    /** 查询当前作者的结算单列表：GET /api/v1/author/settlements（走 Feign 到 moyue-system） */
    @GetMapping("/author/settlements")
    public R<List<SettlementDTO>> listSettlements(HttpServletRequest request) {
        return R.ok(authorService.listSettlements(requireUserId(request)));
    }

    /** 查询单张结算单：GET /api/v1/author/settlements/{id}（走 Feign 到 moyue-system） */
    @GetMapping("/author/settlements/{id}")
    public R<SettlementDTO> getSettlement(@PathVariable Long id) {
        return R.ok(authorService.getSettlement(id));
    }

    private long requireUserId(HttpServletRequest request) {
        String uid = request.getHeader(Constants.USER_ID_HEADER);
        if (uid == null || uid.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(uid.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }
}
