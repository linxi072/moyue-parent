package com.moyue.author.controller;

import com.moyue.author.entity.AuthorIncomeEntity;
import com.moyue.author.service.AuthorService;
import com.moyue.common.core.domain.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者稿酬接口。
 * 路径前缀 /api/v1 与网关路由、Feign 客户端路径保持一致。
 */
@RestController
@RequestMapping("/api/v1")
public class AuthorController {

    @Autowired
    private AuthorService authorService;

    /** 按作者查询稿酬流水：GET /api/v1/author/income/{authorId} */
    @GetMapping("/author/income/{authorId}")
    public R<List<AuthorIncomeEntity>> listIncome(@PathVariable Long authorId) {
        return R.ok(authorService.listByAuthor(authorId));
    }
}
