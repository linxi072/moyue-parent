package com.moyue.risk.sensitive;

import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.security.RequiresPermissions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 敏感词管理接口（后台，P2-15 功能矩阵 M25-M27 / M35-M37）。
 * <p>完整前缀 /api/v1/admin/risk/sensitive-words，匹配网关 /api/v1/admin/risk/** 路由，
 * 由 AdminRoleInterceptor（moyue-common）做 role=3 断言 + @RequiresPermissions 细粒度权限码
 * （system:risk:word:*，菜单种子见 V15 迁移）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/risk/sensitive-words")
public class SensitiveWordAdminController {

    @Autowired
    private SensitiveWordService sensitiveWordService;

    /** 分页查询：GET /api/v1/admin/risk/sensitive-words?page&size&keyword&category&level&status */
    @GetMapping
    @RequiresPermissions("system:risk:word:list")
    public R<PageResult<SensitiveWordEntity>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String category,
                                                   @RequestParam(required = false) Integer level,
                                                   @RequestParam(required = false) Integer status) {
        return R.ok(sensitiveWordService.page(page, size, keyword, category, level, status));
    }

    /** 新增：POST /api/v1/admin/risk/sensitive-words */
    @PostMapping
    @RequiresPermissions("system:risk:word:add")
    public R<SensitiveWordEntity> create(@RequestBody WordReq req) {
        return R.ok(sensitiveWordService.create(req.getWord(), req.getLevel(),
                req.getCategory(), req.getStatus()));
    }

    /** 编辑：PUT /api/v1/admin/risk/sensitive-words/{id} */
    @PutMapping("/{id}")
    @RequiresPermissions("system:risk:word:edit")
    public R<SensitiveWordEntity> update(@PathVariable Long id, @RequestBody WordReq req) {
        return R.ok(sensitiveWordService.update(id, req.getWord(), req.getLevel(),
                req.getCategory(), req.getStatus()));
    }

    /** 删除（逻辑删除）：DELETE /api/v1/admin/risk/sensitive-words/{id} */
    @DeleteMapping("/{id}")
    @RequiresPermissions("system:risk:word:remove")
    public R<Void> delete(@PathVariable Long id) {
        sensitiveWordService.delete(id);
        return R.ok();
    }

    /** 批量停用：POST /api/v1/admin/risk/sensitive-words/batch-disable，body = id 数组 */
    @PostMapping("/batch-disable")
    @RequiresPermissions("system:risk:word:edit")
    public R<Integer> batchDisable(@RequestBody List<Long> ids) {
        return R.ok(sensitiveWordService.batchDisable(ids));
    }

    /** 命中次数统计 TopN：GET /api/v1/admin/risk/sensitive-words/hit-stats?limit=50 */
    @GetMapping("/hit-stats")
    @RequiresPermissions("system:risk:word:list")
    public R<List<SensitiveWordEntity>> hitStats(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(sensitiveWordService.hitStats(limit));
    }

    /** CSV 导入：POST /api/v1/admin/risk/sensitive-words/import（multipart 字段 file，或 body 直传 CSV 文本） */
    @PostMapping("/import")
    @RequiresPermissions("system:risk:word:import")
    public R<Map<String, Integer>> importCsv(@RequestParam(value = "file", required = false) MultipartFile file,
                                             @RequestBody(required = false) String csvBody) {
        String content = null;
        try {
            if (file != null && !file.isEmpty()) {
                content = new String(file.getBytes(), StandardCharsets.UTF_8);
            } else if (csvBody != null && !csvBody.isBlank()) {
                content = csvBody;
            }
        } catch (Exception ex) {
            return R.fail(com.moyue.common.ResultCode.PARAM_ERROR, "读取导入文件失败：" + ex.getMessage());
        }
        int[] r = sensitiveWordService.importCsv(content);
        return R.ok(Map.of("imported", r[0], "skipped", r[1]));
    }

    /** CSV 导出：GET /api/v1/admin/risk/sensitive-words/export */
    @GetMapping("/export")
    @RequiresPermissions("system:risk:word:export")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] csv = sensitiveWordService.exportCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sensitive-words.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    /** 手动刷新词库引擎：PUT /api/v1/admin/risk/sensitive-words/refresh，返回当前启用词数 */
    @PutMapping("/refresh")
    @RequiresPermissions("system:risk:word:refresh")
    public R<Integer> refresh(HttpServletRequest request) {
        return R.ok(sensitiveWordService.refresh());
    }

    // ------------------------------ 请求体 ------------------------------

    /** 敏感词请求体（编辑时仅更新非空字段） */
    public static class WordReq {
        private String word;
        private Integer level;
        private String category;
        private Integer status;

        public String getWord() {
            return word;
        }

        public void setWord(String word) {
            this.word = word;
        }

        public Integer getLevel() {
            return level;
        }

        public void setLevel(Integer level) {
            this.level = level;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }
}
