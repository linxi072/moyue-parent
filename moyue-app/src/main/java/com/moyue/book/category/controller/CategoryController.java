package com.moyue.book.category.controller;

import com.moyue.api.content.dto.CategoryDTO;
import com.moyue.book.category.entity.CategoryEntity;
import com.moyue.book.category.service.CategoryService;
import com.moyue.common.R;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分类接口（P2-A 分类服务独立化）。
 *
 * <p>书城侧 {@code GET /api/v1/categories} 返回全量分类（按 sort 升序），供前端筛选项；
 * POST / PUT / DELETE 为后台增删改，经 moyue-system 的 {@code CategoryAdminController}
 * （RBAC）通过 {@code CategoryClient} 代理调用，本服务仅做数据落地。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /** 书城筛选项：全量分类（按 sort 升序） */
    @GetMapping("/categories")
    public R<List<CategoryDTO>> listCategories() {
        List<CategoryDTO> dtos = categoryService.listAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return R.ok(dtos);
    }

    /** 分类详情 */
    @GetMapping("/categories/{id}")
    public R<CategoryDTO> getCategory(@PathVariable Long id) {
        CategoryEntity e = categoryService.getById(id);
        if (e == null) {
            return R.fail(com.moyue.common.ResultCode.RESOURCE_NOT_FOUND.getCode(), "分类不存在");
        }
        return R.ok(toDto(e));
    }

    /** 新建分类 */
    @PostMapping("/categories")
    public R<CategoryDTO> createCategory(@RequestBody CategoryDTO dto) {
        CategoryEntity e = new CategoryEntity();
        BeanUtils.copyProperties(dto, e);
        e.setId(null);
        CategoryEntity saved = categoryService.create(e);
        return R.ok(toDto(saved));
    }

    /** 编辑分类（仅更新非空字段） */
    @PutMapping("/categories/{id}")
    public R<CategoryDTO> updateCategory(@PathVariable Long id, @RequestBody CategoryDTO dto) {
        CategoryEntity e = new CategoryEntity();
        BeanUtils.copyProperties(dto, e);
        e.setId(id);
        CategoryEntity updated = categoryService.update(e);
        if (updated == null) {
            return R.fail(com.moyue.common.ResultCode.RESOURCE_NOT_FOUND.getCode(), "分类不存在");
        }
        return R.ok(toDto(updated));
    }

    /** 删除分类（逻辑删除） */
    @DeleteMapping("/categories/{id}")
    public R<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteById(id);
        return R.ok();
    }

    /** 实体 → DTO（仅暴露书城 / 后台需要的字段） */
    private CategoryDTO toDto(CategoryEntity e) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setIcon(e.getIcon());
        dto.setSort(e.getSort());
        return dto;
    }
}
