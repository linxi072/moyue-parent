package com.moyue.search.controller;

import com.moyue.common.R;
import com.moyue.search.document.BookDocument;
import com.moyue.search.service.LeaderboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 榜单/排行榜接口（榜单模块）。
 * 路径前缀 /api/v1 与网关路由保持一致。
 *
 * <p>四榜：hot（热门）/ newest（新书）/ finished（完结）/ top-rated（评分），
 * 业务由 {@link LeaderboardService} 经 Elasticsearch 召回并缓存。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class LeaderboardController {

    @Autowired
    private LeaderboardService leaderboardService;

    /**
     * 榜单：type = hot(热门) / newest(新书) / finished(完结) / top-rated(评分)。
     *
     * @param type  榜单类型
     * @param limit 条数（默认 20，上限 100，由服务收敛）
     */
    @GetMapping("/leaderboards/{type}")
    public R<List<BookDocument>> list(@PathVariable String type,
                                      @RequestParam(defaultValue = "20") int limit) {
        return R.ok(leaderboardService.list(type, limit));
    }
}
