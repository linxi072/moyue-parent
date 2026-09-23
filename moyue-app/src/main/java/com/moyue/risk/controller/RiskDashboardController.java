package com.moyue.risk.controller;

import com.moyue.common.R;
import com.moyue.common.security.RequiresPermissions;
import com.moyue.risk.RiskDashboardService;
import com.moyue.risk.RiskDashboardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容安全统计看板接口（P1-4，后台）。
 * <p>完整前缀 /api/v1/admin/risk，匹配网关 moyue-risk-admin 路由 /api/v1/admin/risk/**
 * （application.yml 已配置），由 AdminRoleInterceptor（moyue-common）做 role=3 断言 +
 * @RequiresPermissions 细粒度权限码（system:risk:dashboard，菜单种子见 V17 迁移）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/risk")
public class RiskDashboardController {

    @Autowired
    private RiskDashboardService riskDashboardService;

    /** 统计看板：GET /api/v1/admin/risk/dashboard */
    @GetMapping("/dashboard")
    @RequiresPermissions("system:risk:dashboard")
    public R<RiskDashboardVO> dashboard() {
        return R.ok(riskDashboardService.getDashboard());
    }
}
