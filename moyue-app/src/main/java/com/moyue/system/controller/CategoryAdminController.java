package com.moyue.system.controller;

import com.moyue.api.content.client.CategoryClient;
import com.moyue.api.content.dto.CategoryDTO;
import com.moyue.common.R;
import com.moyue.common.security.RequiresPermissions;
import com.moyue.system.annotation.Log;
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

/**
 * 分类管理接口（P2-A 分类服务独立化，运营后台）。
 *
 * <p>完整前缀 /api/v1/admin/system/category，写操作需 system:category:* 权限码并落操作日志。
 * 实际数据由本控制器经 {@link CategoryClient}（Feign，目标 moyue-content）代理调用内容域落地，
 * 平台运营域自身不持有 category 表，仅做 RBAC 鉴权与降级。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/system/category")
public class CategoryAdminController {

    @Autowired
    private CategoryClient categoryClient;

    /** 分类列表（书城筛选项）：GET /api/v1/admin/system/category/list */
    @GetMapping("/list")
    @RequiresPermissions("system:category:list")
    public R<List<CategoryDTO>> list() {
        return categoryClient.listCategories();
    }

    /** 新建分类：POST /api/v1/admin/system/category */
    @PostMapping
    @RequiresPermissions("system:category:add")
    @Log(module = "分类管理", businessType = Log.BusinessType.INSERT)
    public R<CategoryDTO> create(@RequestBody CategoryDTO dto) {
        return categoryClient.createCategory(dto);
    }

    /** 编辑分类：PUT /api/v1/admin/system/category/{id} */
    @PutMapping("/{id}")
    @RequiresPermissions("system:category:edit")
    @Log(module = "分类管理", businessType = Log.BusinessType.UPDATE)
    public R<CategoryDTO> update(@PathVariable Long id, @RequestBody CategoryDTO dto) {
        return categoryClient.updateCategory(id, dto);
    }

    /** 删除分类：DELETE /api/v1/admin/system/category/{id} */
    @DeleteMapping("/{id}")
    @RequiresPermissions("system:category:remove")
    @Log(module = "分类管理", businessType = Log.BusinessType.DELETE)
    public R<Void> remove(@PathVariable Long id) {
        return categoryClient.deleteCategory(id);
    }
}
