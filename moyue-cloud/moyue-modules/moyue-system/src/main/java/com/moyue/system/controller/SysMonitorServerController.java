package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.common.monitor.collector.ActuatorCollector;
import com.moyue.common.monitor.collector.ServerCollector;
import com.moyue.common.monitor.model.ServerVO;
import com.moyue.common.security.annotation.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务监控（⑫）：5 个端点。数据源为 Spring Boot Actuator + JDK 运行时指标。
 *
 * @author moyue
 */
@Slf4j
@Tag(name = "服务监控", description = "服务器 / JVM / 磁盘概览与实例健康")
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/monitor/server")
@RequiredArgsConstructor
public class SysMonitorServerController {

    private final ServerCollector serverCollector;
    private final ActuatorCollector actuatorCollector;

    @Operation(summary = "服务器概览", description = "CPU / 内存 / 磁盘 / 系统信息 / JVM 版本与启动参数")
    @RequiresPermissions("system:monitor:server")
    @GetMapping
    public R<ServerVO> overview() {
        return R.ok(serverCollector.collect());
    }

    @Operation(summary = "JVM 详情", description = "堆 / 非堆内存、GC、线程数")
    @RequiresPermissions("system:monitor:server")
    @GetMapping("/jvm")
    public R<Map<String, Object>> jvm() {
        ServerVO vo = serverCollector.collect();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("jvm", vo.getJvm());
        jvm.put("metrics", actuatorCollector.metrics());
        jvm.put("health", actuatorCollector.healthStatus());
        return R.ok(jvm);
    }

    @Operation(summary = "磁盘分区明细")
    @RequiresPermissions("system:monitor:server")
    @GetMapping("/disk")
    public R<List<ServerVO.Disk>> disk() {
        return R.ok(serverCollector.collect().getDisks());
    }

    @Operation(summary = "服务实例清单", description = "Cloud 版返回注册中心实例；Boot 版返回单实例")
    @RequiresPermissions("system:monitor:server")
    @GetMapping("/instances")
    public R<List<Map<String, Object>>> instances() {
        ServerVO vo = serverCollector.collect();
        Map<String, Object> instance = new LinkedHashMap<>();
        instance.put("instanceId", "local");
        instance.put("hostName", vo.getSys() == null ? "" : vo.getSys().getComputerName());
        instance.put("ip", vo.getSys() == null ? "" : vo.getSys().getComputerIp());
        instance.put("status", actuatorCollector.healthStatus());
        instance.put("os", vo.getSys() == null ? "" : vo.getSys().getOsName());
        return R.ok(List.of(instance));
    }

    @Operation(summary = "指定实例的监控数据")
    @RequiresPermissions("system:monitor:server")
    @GetMapping("/instances/{instanceId}")
    public R<Map<String, Object>> instance(@PathVariable String instanceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("instanceId", instanceId);
        data.put("server", serverCollector.collect());
        data.put("health", actuatorCollector.healthDetail());
        return R.ok(data);
    }
}
