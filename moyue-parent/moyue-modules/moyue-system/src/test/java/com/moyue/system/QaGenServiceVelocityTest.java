package com.moyue.system;

import com.moyue.common.BizException;
import com.moyue.system.service.gen.GenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GenService Velocity 引擎实测（P2-15 40001 防御性修复验证）。
 *
 * <p>覆盖任务书要求的三个验证点：</p>
 * <ul>
 *   <li>① Velocity 在无 velocity.log 写权限环境下可初始化：真实 new GenService()
 *       （固定 JdkLogChute + resource.loader=classpath 兼容键），构造即 init，不落 velocity.log；</li>
 *   <li>② classpath 模板能加载渲染：mock JDBC 元数据后走真实 preview / downloadZip，
 *       渲染 gen/templates 下全部 8 个 .vm 模板；</li>
 *   <li>③ 表名白名单异常映射为业务异常（非未知 40001）。</li>
 * </ul>
 */
class QaGenServiceVelocityTest {

    /** 与 GenService.TEMPLATE_MAPPINGS 一致：8 个模板文件 */
    private static final int TEMPLATE_COUNT = 8;

    @Test
    @DisplayName("真实构造（Velocity init）成功：classpath 加载器 + JdkLogChute，不写 velocity.log")
    void constructorShouldInitVelocityEngineWithoutFileLog() {
        long before = System.currentTimeMillis();
        GenService service = new GenService();
        assertThat(service).isNotNull();
        // JdkLogChute 路径下不应在工作目录产生 velocity.log（若产生，说明日志配置退化）
        java.io.File logFile = new java.io.File("velocity.log");
        if (logFile.exists() && logFile.lastModified() >= before) {
            logFile.deleteOnExit();
            throw new AssertionError("Velocity 初始化在工作目录创建了 velocity.log，JdkLogChute 配置未生效");
        }
    }

    @Test
    @DisplayName("classpath 模板真实渲染：preview 输出 8 个文件且内容含生成的类名/包名/表名")
    void previewShouldRenderAllTemplatesFromClasspath() throws Exception {
        GenService service = new GenService();
        injectDataSource(service, mockMetadataDataSource());

        Map<String, String> preview = service.preview("qa_demo_user");

        assertThat(preview).hasSize(TEMPLATE_COUNT);
        assertThat(preview).containsKeys(
                "QaDemoUserEntity.java", "QaDemoUserMapper.java", "QaDemoUserMapperXml.xml",
                "QaDemoUserService.java", "QaDemoUserServiceImpl.java", "QaDemoUserController.java",
                "menu.sql", "index.html");

        String entity = preview.get("QaDemoUserEntity.java");
        assertThat(entity)
                .contains("package com.moyue.system.entity;")
                .contains("public class QaDemoUserEntity implements Serializable")
                .contains("@TableName(\"qa_demo_user\")")
                .contains("private Long id;")
                .contains("private String userName;")
                .contains("private java.time.LocalDateTime createTime;")
                .doesNotContain("${"); // 未渲染占位符残留

        String controller = preview.get("QaDemoUserController.java");
        assertThat(controller).contains("com.moyue.system").doesNotContain("${");
        String menu = preview.get("menu.sql");
        assertThat(menu).doesNotContain("${");
    }

    @Test
    @DisplayName("downloadZip：输出 zip 含 8 个条目，UTF-8 可解析")
    void downloadZipShouldPackageAllRenderedFiles() throws Exception {
        GenService service = new GenService();
        injectDataSource(service, mockMetadataDataSource());

        byte[] zipBytes = service.downloadZip("qa_demo_user");
        assertThat(zipBytes).isNotEmpty();

        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                assertThat(entry.getName()).startsWith("moyue-gen-qa_demo_user/");
            }
        }
        assertThat(entries).isEqualTo(TEMPLATE_COUNT);
    }

    @Test
    @DisplayName("非法表名（白名单外）→ PARAM_ERROR 业务异常，而非 40001 未知错误")
    void invalidTableNameShouldMapToBizException() throws Exception {
        GenService service = new GenService();
        injectDataSource(service, mockMetadataDataSource());

        assertThatThrownBy(() -> service.preview("user; DROP TABLE t;--"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.preview(null))
                .isInstanceOf(BizException.class);
    }

    // ------------------------------ JDBC 元数据 mock ------------------------------

    /**
     * mock information_schema 查询：主键(statistics) / 列(columns) / 表注释(tables)。
     * 返回表 qa_demo_user：id bigint PK、user_name varchar、create_time datetime。
     */
    private static DataSource mockMetadataDataSource() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conn);

        // 主键查询（information_schema.statistics）→ 一行 id
        PreparedStatement pkPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(org.mockito.ArgumentMatchers.contains("information_schema.statistics")))
                .thenReturn(pkPs);
        ResultSet pkRs = mock(ResultSet.class);
        when(pkPs.executeQuery()).thenReturn(pkRs);
        when(pkRs.next()).thenReturn(true, false);
        when(pkRs.getString("column_name")).thenReturn("id");

        // 列查询（information_schema.columns）→ 三行
        PreparedStatement colPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(org.mockito.ArgumentMatchers.contains("information_schema.columns")))
                .thenReturn(colPs);
        ResultSet colRs = mock(ResultSet.class);
        when(colPs.executeQuery()).thenReturn(colRs);
        AtomicInteger row = new AtomicInteger(0);
        when(colRs.next()).thenAnswer(inv -> row.getAndIncrement() < 3);
        when(colRs.getString("column_name")).thenReturn("id", "user_name", "create_time");
        when(colRs.getString("data_type")).thenReturn("bigint", "varchar", "datetime");
        when(colRs.getString("column_type")).thenReturn("bigint", "varchar(64)", "datetime");
        when(colRs.getString("column_comment")).thenReturn("主键", "用户名", "创建时间");
        when(colRs.getString("is_nullable")).thenReturn("NO", "NO", "NO");
        when(colRs.getString("column_default")).thenReturn(null, null, null);

        // 表注释查询（information_schema.tables）→ 一行
        PreparedStatement commentPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(org.mockito.ArgumentMatchers.contains("information_schema.tables")))
                .thenReturn(commentPs);
        ResultSet commentRs = mock(ResultSet.class);
        when(commentPs.executeQuery()).thenReturn(commentRs);
        when(commentRs.next()).thenReturn(true, false);
        when(commentRs.getString(1)).thenReturn("演示用户");

        return dataSource;
    }

    private static void injectDataSource(GenService service, DataSource dataSource) throws Exception {
        java.lang.reflect.Field f = GenService.class.getDeclaredField("dataSource");
        f.setAccessible(true);
        f.set(service, dataSource);
    }
}
