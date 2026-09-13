package com.moyue.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V15 迁移 SQL 可解析性单测（H2 MySQL 兼容模式）—— P2-15 内容安全收尾。
 *
 * <p>校验内容：</p>
 * <ul>
 *   <li>ALTER TABLE sensitive_word ADD COLUMN hit_count 可执行（V13 建表 → V15 加列不冲突）；</li>
 *   <li>hit_count 列属性：NOT NULL、默认 0、位于 status 之后（AFTER status）；</li>
 *   <li>菜单种子：1 目录 + 2 页面菜单 + 9 按钮 = 12 行，ID 无重复、parent 外键自洽；</li>
 *   <li>权限码：system:risk:word:* 7 个 + system:report:* 2 个，与控制器注解一一对应；</li>
 *   <li>菜单 ID 段（940000000000001004 / 1401 / 1402 / 2181~2187 / 2191 / 2192）不与 V14 种子冲突。</li>
 * </ul>
 */
class QaV15SqlParseTest {

    private static final String V15 = "/db/migration/V15__risk_hit_count_and_risk_menus.sql";
    private static final String V14 = "/db/migration/V14__system_dict_config_log.sql";

    /** V12 的 sys_menu 建表语句（V15 菜单种子依赖；仅取使用到的列） */
    private static final String SYS_MENU_DDL = """
            CREATE TABLE IF NOT EXISTS `sys_menu` (
              `id`          BIGINT       NOT NULL,
              `parent_id`   BIGINT       NOT NULL DEFAULT 0,
              `menu_name`   VARCHAR(50)  NOT NULL,
              `menu_type`   TINYINT      NOT NULL DEFAULT 1,
              `path`        VARCHAR(128) DEFAULT NULL,
              `component`   VARCHAR(128) DEFAULT NULL,
              `perms`       VARCHAR(128) DEFAULT NULL,
              `icon`        VARCHAR(64)  DEFAULT NULL,
              `order_num`   INT          NOT NULL DEFAULT 0,
              `status`      TINYINT      NOT NULL DEFAULT 1,
              `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
              `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
              `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`)
            );
            """;

    /** V13 的 sensitive_word 建表语句（V15 ALTER 的前置表结构） */
    private static final String SENSITIVE_WORD_DDL = """
            CREATE TABLE IF NOT EXISTS `sensitive_word` (
              `id`          BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
              `word`        VARCHAR(64)  NOT NULL                COMMENT '敏感词',
              `level`       TINYINT      NOT NULL DEFAULT 1      COMMENT '等级：1 拦截 / 2 告警（转人工）',
              `category`    VARCHAR(32)  DEFAULT NULL            COMMENT '分类：政治/广告/谩骂/涉黄…',
              `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 停用 / 1 启用',
              `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除',
              `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
              `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_word` (`word`),
              KEY `idx_status_level` (`status`, `level`)
            );
            """;

    /** V15 预期的 9 个按钮权限码（与 SensitiveWordAdminController / ReportAdminController 注解一致） */
    private static final List<String> EXPECTED_PERMS = List.of(
            "system:risk:word:list", "system:risk:word:add", "system:risk:word:edit",
            "system:risk:word:remove", "system:risk:word:import", "system:risk:word:export",
            "system:risk:word:refresh",
            "system:report:list", "system:report:handle");

    @Test
    @DisplayName("V15 全量脚本在 H2(MySQL 模式) 可执行：hit_count 加列 + 菜单种子完整落库且与 V14 无 ID 冲突")
    void v15ScriptShouldExecuteAndSeed() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream(V15)) {
            assertThat(in).as("classpath 资源 %s 必须存在", V15).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:v15qa;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;"
                        + "DB_CLOSE_DELAY=-1", "sa", "")) {
            // 前置：V15 依赖 V12 sys_menu 与 V13 sensitive_word
            try (Statement st = conn.createStatement()) {
                st.execute(SYS_MENU_DDL);
                st.execute(SENSITIVE_WORD_DDL);
                st.execute("INSERT INTO `sensitive_word` (`id`,`word`,`level`,`status`,`is_deleted`) "
                        + "VALUES (1,'赌博',1,1,0)");
            }

            // 先落 V14 菜单种子，再执行 V15：若 V15 的菜单 ID 与 V14 冲突，
            // INSERT IGNORE 会静默跳过 → 行数/存在性断言失败（严格的冲突检测）
            runScript(conn, V14);
            int menusBeforeV15 = countRows(conn, "sys_menu");
            int dirsBeforeV15 = countMenuByType(conn, 0);
            int pagesBeforeV15 = countMenuByType(conn, 1);
            int buttonsBeforeV15 = countMenuByType(conn, 2);

            org.h2.tools.RunScript.execute(conn, new java.io.InputStreamReader(
                    new java.io.ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8));

            // ---- hit_count 列断言 ----
            assertThat(columnExists(conn, "sensitive_word", "hit_count"))
                    .as("V15 必须为 sensitive_word 增加 hit_count 列").isTrue();
            java.util.Map<?, ?> column = columnMeta(conn, "sensitive_word", "hit_count");
            assertThat(column.get("nullable")).as("hit_count 必须 NOT NULL").isEqualTo("NO");
            assertThat(String.valueOf(column.get("default"))).as("hit_count 默认 0").isEqualTo("0");
            assertThat(column.get("afterColumn")).as("hit_count 位于 status 之后（AFTER status）")
                    .isEqualTo("status");
            // 既有数据默认回填 0
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT hit_count FROM `sensitive_word` WHERE id = 1")) {
                rs.next();
                assertThat(rs.getInt(1)).as("既有行 hit_count 默认 0").isZero();
            }

            // ---- 菜单种子断言 ----
            assertThat(countRows(conn, "sys_menu")).as("V14 之后新增菜单种子总行数（+12）")
                    .isEqualTo(menusBeforeV15 + 12);
            assertThat(countMenuByType(conn, 0) - dirsBeforeV15).as("新增目录数（内容安全）").isEqualTo(1);
            assertThat(countMenuByType(conn, 1) - pagesBeforeV15).as("新增页面菜单数（敏感词/举报）").isEqualTo(2);
            assertThat(countMenuByType(conn, 2) - buttonsBeforeV15).as("新增按钮数").isEqualTo(9);

            // ID 无重复
            List<Long> ids = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id FROM `sys_menu`")) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
            assertThat(ids).doesNotHaveDuplicates();
            assertThat(new HashSet<>(ids)).hasSize(menusBeforeV15 + 12);

            // parent 自洽：所有 parent_id != 0 必须指向存在的菜单
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM `sys_menu` c LEFT JOIN `sys_menu` p ON c.parent_id = p.id "
                                 + "WHERE c.parent_id <> 0 AND p.id IS NULL")) {
                rs.next();
                assertThat(rs.getInt(1)).as("菜单 parent 外键自洽").isZero();
            }

            // 权限码：V15 的 9 个新权限码必须全部落库（表内同时含 V14 既有码，故用 containsAll）
            Set<String> perms = new HashSet<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT perms FROM `sys_menu` WHERE perms IS NOT NULL")) {
                while (rs.next()) {
                    perms.add(rs.getString(1));
                }
            }
            assertThat(perms).containsAll(EXPECTED_PERMS);

            // 与 V14 种子的 ID 段无冲突：先落 V14 再跑 V15（INSERT IGNORE 语义），
            // 上面「总数 +12 且 12 个新 ID 全部存在」的断言即为严格冲突检测
            for (long id : new long[]{940000000000001004L, 940000000000001401L, 940000000000001402L,
                    940000000000002181L, 940000000000002182L, 940000000000002183L, 940000000000002184L,
                    940000000000002185L, 940000000000002186L, 940000000000002187L,
                    940000000000002191L, 940000000000002192L}) {
                assertThat(ids).as("V15 菜单 ID %d 必须落库（若与 V14 冲突会被 INSERT IGNORE 静默丢弃）", id)
                        .contains(id);
            }
        }
    }

    // ------------------------------ 工具 ------------------------------

    /** 执行 classpath 下的迁移脚本（H2 工具级解析器，按分号切分，自带 -- 注释处理） */
    private void runScript(Connection conn, String classpath) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("classpath 资源 %s 必须存在", classpath).isNotNull();
            org.h2.tools.RunScript.execute(conn, new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private int countRows(Connection conn, String table) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countMenuByType(Connection conn, int menuType) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM `sys_menu` WHERE menu_type = ?")) {
            ps.setInt(1, menuType);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** 列元数据：nullable / default / 前一列名（AFTER 语义校验用，按 ORDINAL_POSITION 推导） */
    private java.util.HashMap<String, Object> columnMeta(Connection conn, String table, String column)
            throws Exception {
        java.util.HashMap<String, Object> meta = new java.util.HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name, is_nullable, column_default, ordinal_position "
                        + "FROM information_schema.columns WHERE table_name = ? ORDER BY ordinal_position")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                String prev = null;
                while (rs.next()) {
                    String name = rs.getString("column_name");
                    if (column.equalsIgnoreCase(name)) {
                        meta.put("nullable", rs.getString("is_nullable"));
                        meta.put("default", rs.getString("column_default"));
                        meta.put("afterColumn", prev);
                        meta.put("position", rs.getInt("ordinal_position"));
                    }
                    prev = name;
                }
            }
        }
        return meta;
    }
}
