package com.moyue.api.content.client;

import com.moyue.api.content.dto.CategoryDTO;
import com.moyue.book.category.entity.CategoryEntity;
import com.moyue.book.category.service.CategoryService;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分类服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-content) 已移除 OpenFeign，改为直接注入 {@link CategoryService} 委托调用。
 */
@Component
public class CategoryClient {

    private final CategoryService categoryService;

    public CategoryClient(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 全量分类（书城筛选项） */
    public R<List<CategoryDTO>> listCategories() {
        try {
            return R.ok(toDtoList(categoryService.listAll()));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 分类详情 */
    public R<CategoryDTO> getCategory(Long id) {
        try {
            return R.ok(toDto(categoryService.getById(id)));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 新建分类 */
    public R<CategoryDTO> createCategory(CategoryDTO dto) {
        try {
            return R.ok(toDto(categoryService.create(toEntity(dto))));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 编辑分类 */
    public R<CategoryDTO> updateCategory(Long id, CategoryDTO dto) {
        try {
            CategoryEntity e = toEntity(dto);
            e.setId(id);
            return R.ok(toDto(categoryService.update(e)));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 删除分类（逻辑删除） */
    public R<Void> deleteCategory(Long id) {
        try {
            categoryService.deleteById(id);
            return R.ok();
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    private CategoryDTO toDto(CategoryEntity e) {
        if (e == null) {
            return null;
        }
        CategoryDTO d = new CategoryDTO();
        d.setId(e.getId());
        d.setName(e.getName());
        d.setIcon(e.getIcon());
        d.setSort(e.getSort());
        return d;
    }

    private List<CategoryDTO> toDtoList(List<CategoryEntity> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    private CategoryEntity toEntity(CategoryDTO d) {
        CategoryEntity e = new CategoryEntity();
        e.setName(d.getName());
        e.setIcon(d.getIcon());
        e.setSort(d.getSort());
        e.setStatus(1);
        return e;
    }
}
