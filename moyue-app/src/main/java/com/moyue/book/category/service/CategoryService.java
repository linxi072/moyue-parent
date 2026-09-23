package com.moyue.book.category.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.book.category.entity.CategoryEntity;
import com.moyue.book.category.mapper.CategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类领域服务（P2-A 分类服务独立化）。
 *
 * <p>替代原 {@code BookService.CATEGORY_NAMES} 硬编码字典：分类名经本服务从 category 表解析，
 * 运营后台可增删改排序。逻辑删除走 {@link CategoryEntity#getIsDeleted()} 的
 * {@code @TableLogic}，删除后查询自动排除。</p>
 *
 * <p>读取策略：每次需要时查（简单实现，避免缓存陈旧）。列表按 {@code sort} 升序返回，
 * 供书城前端筛选项与检索载荷使用。</p>
 */
@Service
public class CategoryService {

    /** 查不到分类时的回退名 */
    private static final String UNKNOWN = "未知";

    @Autowired
    private CategoryMapper categoryMapper;

    /**
     * 全量分类（按 sort 升序）。逻辑删除行被 {@code @TableLogic} 自动排除。
     *
     * @return 分类列表（空列表而非 null）
     */
    public List<CategoryEntity> listAll() {
        LambdaQueryWrapper<CategoryEntity> q = new LambdaQueryWrapper<CategoryEntity>()
                .orderByAsc(CategoryEntity::getSort);
        return new ArrayList<>(categoryMapper.selectList(q));
    }

    /**
     * 按主键查询分类。逻辑删除行为 {@code @TableLogic} 自动排除，故已删除返回 null。
     *
     * @param id 分类 ID（null 直接返回 null）
     * @return 分类实体或 null
     */
    public CategoryEntity getById(Long id) {
        if (id == null) {
            return null;
        }
        return categoryMapper.selectById(id);
    }

    /**
     * 解析分类名（供 BookService / 索引载荷复用）。查不到回退 {@code "未知"}。
     *
     * @param id 分类 ID（null 回退 {@code "未知"}）
     * @return 分类名或 {@code "未知"}
     */
    public String resolveName(Long id) {
        CategoryEntity e = getById(id);
        return e == null ? UNKNOWN : e.getName();
    }

    /**
     * 新建分类。id 由 {@code @TableId(ASSIGN_ID)} 自动生成。
     *
     * @param entity 含 name / icon / sort / status 的分类实体（id 留空）
     * @return 落库后的实体（含生成 id）
     */
    @Transactional
    public CategoryEntity create(CategoryEntity entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getSort() == null) {
            entity.setSort(0);
        }
        categoryMapper.insert(entity);
        return entity;
    }

    /**
     * 更新分类（仅更新非空字段）。
     *
     * @param entity 含 id 与待更新字段的分类实体
     * @return 更新后的实体（查不到返回 null）
     */
    @Transactional
    public CategoryEntity update(CategoryEntity entity) {
        if (entity.getId() == null) {
            return null;
        }
        CategoryEntity existing = categoryMapper.selectById(entity.getId());
        if (existing == null) {
            return null;
        }
        if (entity.getName() != null) {
            existing.setName(entity.getName());
        }
        if (entity.getIcon() != null) {
            existing.setIcon(entity.getIcon());
        }
        if (entity.getSort() != null) {
            existing.setSort(entity.getSort());
        }
        if (entity.getStatus() != null) {
            existing.setStatus(entity.getStatus());
        }
        categoryMapper.updateById(existing);
        return existing;
    }

    /**
     * 逻辑删除分类（@TableLogic → UPDATE is_deleted=1，非物理 DELETE）。
     * 删除后 getById / listAll 自动排除该行。
     *
     * @param id 分类 ID
     */
    @Transactional
    public void deleteById(Long id) {
        categoryMapper.deleteById(id);
    }
}
