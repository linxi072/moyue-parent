package com.moyue.search.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.search.domain.vo.SearchHotWordVO;
import com.moyue.search.service.SearchHotWordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索发现（C 端用户，公开只读）。
 *
 * <p>热词榜与搜索联想均为公开发现能力，无用户归属，不接收也不依赖前端 userId。
 * 网关路由 {@code /api/v1/search/**} 已指向本服务，无需新增路由。
 *
 * @author moyue
 */
@Tag(name = "搜索发现", description = "热词榜、搜索联想（前缀匹配）")
@Validated
@RestController
@RequestMapping(Constants.API_PREFIX + "/search")
@RequiredArgsConstructor
public class SearchConsumerController {

    private final SearchHotWordService hotWordService;

    @Operation(summary = "热词榜", description = "启用中热词按权重倒序，limit 收敛到 [1, 20]")
    @GetMapping("/hot-words")
    public R<List<SearchHotWordVO>> hotWords(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(hotWordService.consumerHotWords(limit));
    }

    @Operation(summary = "搜索联想", description = "按输入前缀匹配启用中热词，空词返回空列表")
    @GetMapping("/suggest")
    public R<List<SearchHotWordVO>> suggest(@RequestParam String keyword) {
        return R.ok(hotWordService.consumerSuggest(keyword));
    }
}
