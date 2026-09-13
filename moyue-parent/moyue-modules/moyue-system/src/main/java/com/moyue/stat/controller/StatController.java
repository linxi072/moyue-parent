package com.moyue.stat.controller;

import com.moyue.common.core.domain.PageResult;
import com.moyue.common.R;
import com.moyue.stat.service.StatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 统计接口：全站聚合统计 + 作者维度下钻 + 留存漏斗 + 明细导出。
 * 网关将 /api/v1/admin/stats/** 路由到本服务，已由网关通配覆盖，无需改网关配置。
 */
@RestController
@RequestMapping("/api/v1")
public class StatController {

    /** 导出文件名时间戳：moyue-{type}-{yyyyMMddHHmmss}.csv */
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private StatService statService;

    @GetMapping("/admin/stats/overview")
    public R<Map<String, Long>> overview() {
        return R.ok(statService.overview());
    }

    /**
     * 作者维度统计分页列表。
     *
     * @param keyword 昵称模糊匹配，可为空
     * @param page    页码，从 1 开始，默认 1
     * @param size    每页大小，默认 20，上限 200
     * @return R&lt;PageResult&lt;Map&lt;String, Object&gt;&gt;&gt;
     */
    @GetMapping("/admin/stats/authors")
    public R<PageResult<Map<String, Object>>> authors(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return R.ok(statService.authorStats(keyword, page, size));
    }

    /**
     * 留存漏斗：registered / activated / paying / active7d 及派生比率。
     *
     * @return R&lt;Map&lt;String, Long&gt;&gt;，比率字段为整数百分比（0~100）
     */
    @GetMapping("/admin/stats/retention")
    public R<Map<String, Long>> retention() {
        return R.ok(statService.retention());
    }

    /**
     * 明细导出 CSV（authors / retention）。
     *
     * <p><b>本端点刻意不走 R&lt;T&gt; 包装</b>：CSV 是二进制/流式下载协议，前端与浏览器依赖
     * Content-Type 与 Content-Disposition 直接触发另存，若再包一层 {code,message,data}
     * 的 JSON 信封，下载到的文件会是含 JSON 外壳的脏数据。</p>
     *
     * <p>type 仅允许 authors / retention 两个枚举值（在 Service 侧二次校验），
     * 避免用户输入污染 Content-Disposition 头造成响应头注入。</p>
     *
     * @param type 导出类型：authors（作者维度） / retention（留存漏斗）
     * @return 带 UTF-8 BOM 的 CSV 字节流
     */
    @GetMapping(value = "/admin/stats/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(@RequestParam String type) {
        byte[] body = statService.exportCsv(type);
        // type 已在 Service 侧限定为白名单常量，此处可安全拼进文件名
        String filename = "moyue-" + type.toLowerCase() + "-" + FILE_STAMP.format(LocalDateTime.now()) + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        headers.setContentLength(body.length);
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
