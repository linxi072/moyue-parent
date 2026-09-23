package com.moyue.book.category.service;

import com.moyue.book.category.entity.CategoryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分类领域集成测试（P2-A 分类服务独立化）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表 + V21 种子（玄幻/都市/悬疑）。
 * 覆盖：create → getById → update → listAll(sort) → 逻辑删除 → 删后查不到；
 * 断言逻辑删除是 UPDATE is_deleted=1 而非物理 DELETE（@TableLogic 生效）。
 * profile 固定为 {@code test}（见 src/test/resources/application-test.yml）。
 */
@SpringBootTest
@ActiveProfiles("test")
class CategoryServiceFlowTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void create_getById_update_listAll_logicDelete_flow() {
        // ---- create ----
        CategoryEntity created = categoryService.create(buildCategory("测试分类", "test", 99));
        Long id = created.getId();
        assertThat(id).isNotNull();

        // ---- getById ----
        CategoryEntity fetched = categoryService.getById(id);
        assertThat(fetched).isNotNull();
        assertThat(fetched.getName()).isEqualTo("测试分类");

        // ---- update（仅更新非空字段）----
        CategoryEntity upd = new CategoryEntity();
        upd.setId(id);
        upd.setName("测试分类-改");
        upd.setSort(100);
        CategoryEntity updated = categoryService.update(upd);
        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("测试分类-改");
        assertThat(updated.getSort()).isEqualTo(100);

        // ---- listAll 按 sort 升序，含种子分类与该分类 ----
        List<CategoryEntity> all = categoryService.listAll();
        assertThat(all).extracting(CategoryEntity::getName)
                .contains("玄幻", "都市", "悬疑", "测试分类-改");
        assertThat(all.get(0).getSort()).isLessThanOrEqualTo(all.get(all.size() - 1).getSort());

        // ---- 逻辑删除 ----
        categoryService.deleteById(id);

        // 删除后 getById 返回 null（@TableLogic 自动排除）
        assertThat(categoryService.getById(id)).isNull();

        // 断言 DB：逻辑删除 = UPDATE is_deleted=1，而非物理 DELETE
        Integer isDeleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM category WHERE id = ?", Integer.class, id);
        assertThat(isDeleted).isEqualTo(1);
        // 物理行仍存在
        Integer physicalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category WHERE id = ?", Integer.class, id);
        assertThat(physicalRows).isEqualTo(1);
        // 活跃（未删除）行不存在
        Integer activeRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category WHERE id = ? AND is_deleted = 0", Integer.class, id);
        assertThat(activeRows).isEqualTo(0);
    }

    @Test
    void listAll_returnsSeededCategoriesSortedBySort() {
        List<CategoryEntity> all = categoryService.listAll();
        assertThat(all).hasSizeGreaterThanOrEqualTo(3);
        // V21 种子：玄幻(sort=1) / 都市(sort=2) / 悬疑(sort=3)，按 sort 升序
        assertThat(all.subList(0, 3)).extracting(CategoryEntity::getSort)
                .containsExactly(1, 2, 3);
        assertThat(all.subList(0, 3)).extracting(CategoryEntity::getName)
                .containsExactly("玄幻", "都市", "悬疑");
    }

    @Test
    void getById_unknownReturnsNull_andResolveNameFallsBack() {
        assertThat(categoryService.getById(999999L)).isNull();
        assertThat(categoryService.resolveName(999999L)).isEqualTo("未知");
        assertThat(categoryService.resolveName(null)).isEqualTo("未知");
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
