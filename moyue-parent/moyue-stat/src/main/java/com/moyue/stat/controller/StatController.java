package com.moyue.stat.controller;

import com.moyue.common.R;
import com.moyue.stat.service.StatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 统计接口：全站聚合统计。
 * 网关将 /api/v1/admin/stats/** 路由到本服务。
 */
@RestController
@RequestMapping("/api/v1")
public class StatController {

    @Autowired
    private StatService statService;

    @GetMapping("/admin/stats/overview")
    public R<Map<String, Long>> overview() {
        return R.ok(statService.overview());
    }
}
