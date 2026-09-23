package com.moyue.system.controller;

import com.moyue.common.R;
import com.moyue.system.service.MonitorService;
import com.moyue.system.vo.DbPoolVO;
import com.moyue.system.vo.ServerMonitorVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务/数据监控接口。
 * 完整前缀 /api/v1/admin/system/monitor（只读查询，不落操作日志）。
 */
@RestController
@RequestMapping("/api/v1/admin/system/monitor")
public class SysMonitorController {

    @Autowired
    private MonitorService monitorService;

    /** 服务器监控（JVM/CPU/内存/磁盘/线程）：GET /api/v1/admin/system/monitor/server */
    @GetMapping("/server")
    public R<ServerMonitorVO> server() {
        return R.ok(monitorService.server());
    }

    /** 数据库连接池监控（HikariCP）：GET /api/v1/admin/system/monitor/dbpool */
    @GetMapping("/dbpool")
    public R<DbPoolVO> dbPool() {
        return R.ok(monitorService.dbPool());
    }

    /** 慢 SQL 概览（在途查询 + digest TopN）：GET /api/v1/admin/system/monitor/sql */
    @GetMapping("/sql")
    public R<Map<String, Object>> slowSql() {
        return R.ok(monitorService.slowSql());
    }
}
