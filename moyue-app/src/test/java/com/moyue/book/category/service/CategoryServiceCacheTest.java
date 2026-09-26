package com.moyue.book.category.service;

import com.moyue.book.category.entity.CategoryEntity;
import com.moyue.book.category.mapper.CategoryMapper;
import com.moyue.common.cache.CacheNames;
import com.moyue.common.cache.LocalCacheTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 分类缓存命中 / 失效集成测试（性能与缓存模块）。
 * 通过 {@link LocalCacheTestConfig} 注入真实本地 CacheManager，使 {@code @Cacheable} 生效；
 * 用 {@link SpyBean} 监听 {@link CategoryMapper#selectList} 调用次数，证明缓存命中与写后失效。
 * 每个测试前清空缓存 + 重置 spy，确保从冷缓存开始（缓存跨测试方法共享于同一上下文）。
 * H2 内存库（MySQL 兼容模式），profile={@code test}。
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(LocalCacheTestConfig.class)
class CategoryServiceCacheTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CacheManager cacheManager;

    @SpyBean
    private CategoryMapper categoryMapper;

    /** 每个测试前清空分类缓存 + 重置 spy，保证从冷缓存开始，调用次数断言才有意义 */
    @BeforeEach
    void resetSpyAndCache() {
        reset(categoryMapper);
        Cache categoryCache = cacheManager.getCache(CacheNames.CATEGORY);
        if (categoryCache != null) {
            categoryCache.clear();
        }
    }

    /** 连续两次 listAll：第二次命中缓存，selectList 仅被调用 1 次 */
    @Test
    void listAll_isCached_acrossCalls() {
        categoryService.listAll();
        categoryService.listAll();

        verify(categoryMapper, times(1)).selectList(any());
    }

    /** create 后整体失效 listAll 缓存，下次 listAll 回源（selectList 共 2 次），且含新分类 */
    @Test
    void create_invalidatesListCache_thenReQuery() {
        categoryService.listAll();                                   // 写缓存（1）
        categoryService.create(buildCategory("缓存失效验证", "c", 50)); // 失效
        categoryService.listAll();                                   // 失效后回源（2）

        verify(categoryMapper, times(2)).selectList(any());
        assertThat(categoryService.listAll()).extracting(CategoryEntity::getName)
                .contains("缓存失效验证");
    }

    /** update 后整体失效 listAll 缓存，下次 listAll 回源（selectList 共 2 次），且反映新名 */
    @Test
    void update_invalidatesListCache_thenReQuery() {
        CategoryEntity created = categoryService.create(buildCategory("待改名", "u", 60));
        categoryService.listAll();                                   // 写缓存（1）

        CategoryEntity upd = new CategoryEntity();
        upd.setId(created.getId());
        upd.setName("已改名");
        categoryService.update(upd);                                 // 失效（内部 selectById 不计入 list）

        List<CategoryEntity> after = categoryService.listAll();     // 失效后回源（2）
        verify(categoryMapper, times(2)).selectList(any());
        assertThat(after).extracting(CategoryEntity::getName).contains("已改名");
    }

    /** deleteById 后整体失效 listAll 缓存，下次 listAll 回源（selectList 共 2 次），且已不含被删分类 */
    @Test
    void deleteById_invalidatesListCache_thenReQuery() {
        CategoryEntity created = categoryService.create(buildCategory("待删除", "d", 70));
        categoryService.listAll();                                   // 写缓存（1）

        categoryService.deleteById(created.getId());                 // 失效

        List<CategoryEntity> after = categoryService.listAll();      // 失效后回源（2）
        verify(categoryMapper, times(2)).selectList(any());
        assertThat(after).extracting(CategoryEntity::getName).doesNotContain("待删除");
    }

    private CategoryEntity buildCategory(String name, String icon, int sort) {
        CategoryEntity e = new CategoryEntity();
        e.setName(name);
        e.setIcon(icon);
        e.setSort(sort);
        e.setStatus(1);
        return e;
    }
}
