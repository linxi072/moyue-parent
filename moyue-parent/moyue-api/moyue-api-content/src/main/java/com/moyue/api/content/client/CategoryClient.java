package com.moyue.api.content.client;

import com.moyue.api.content.dto.CategoryDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 分类服务 Feign 客户端（moyue-content）。
 *
 * <p>contextId：与同服务的 BookClient（name 同为 moyue-content）区分注册，避免
 * FeignClientSpecification 同名 bean 冲突（正解，替代 allow-bean-definition-overriding）。</p>
 *
 * <p>运营后台（moyue-system）经本客户端代理调用 content 的分类增删改查，复用 RBAC 与降级。</p>
 */
@FeignClient(name = "moyue-content", contextId = "categoryClient", fallbackFactory = CategoryClientFallbackFactory.class)
public interface CategoryClient {

    /** 全量分类（书城筛选项） */
    @GetMapping("/api/v1/categories")
    R<List<CategoryDTO>> listCategories();

    /** 分类详情 */
    @GetMapping("/api/v1/categories/{id}")
    R<CategoryDTO> getCategory(@PathVariable("id") Long id);

    /** 新建分类 */
    @PostMapping("/api/v1/categories")
    R<CategoryDTO> createCategory(@RequestBody CategoryDTO dto);

    /** 编辑分类 */
    @PutMapping("/api/v1/categories/{id}")
    R<CategoryDTO> updateCategory(@PathVariable("id") Long id, @RequestBody CategoryDTO dto);

    /** 删除分类（逻辑删除） */
    @DeleteMapping("/api/v1/categories/{id}")
    R<Void> deleteCategory(@PathVariable("id") Long id);
}
