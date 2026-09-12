package com.moyue.read.controller;

import com.moyue.common.R;
import com.moyue.read.entity.BookshelfEntity;
import com.moyue.read.service.ReadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阅读接口：书架查询。
 * 路径前缀 /api/v1 与网关路由保持一致。
 */
@RestController
@RequestMapping("/api/v1")
public class ReadController {

    @Autowired
    private ReadService readService;

    /** 按用户 ID 获取书架 */
    @GetMapping("/read/bookshelf/{userId}")
    public R<List<BookshelfEntity>> getBookshelf(@PathVariable Long userId) {
        return R.ok(readService.getShelf(userId));
    }
}
