package com.moyue.system.service.gen;

import com.moyue.common.exception.BizException;
import com.moyue.common.core.domain.ResultCode;
import lombok.Data;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成器业务（GET /gen/tables、/gen/columns/{table}、/gen/preview/{table}、POST /gen/download/{table}）。
 *
 * <p>流程：读 information_schema.columns / statistics 得到表与列元数据 →
 * Velocity 渲染 classpath:gen/templates/*.vm → ZipOutputStream 打包下载。
 * 目标为单表 CRUD（MyBatis-Plus 版），默认包路径 com.moyue.system。</p>
 */
@Service
public class GenService {

    private static final Logger log = LoggerFactory.getLogger(GenService.class);

    /** 生成代码的默认目标包（本模块） */
    private static final String TARGET_PACKAGE = "com.moyue.system";

    /** 生成代码的 Controller 基路径（与本模块接口风格一致） */
    private static final String CONTROLLER_BASE = "/api/v1/admin/system";

    /** Velocity 模板 classpath 目录 */
    private static final String TEMPLATE_PATH = "gen/templates/";

    /** 输出文件名 → 模板名 */
    private static final String[][] TEMPLATE_MAPPINGS = {
            {"Entity.java", "Entity.java.vm"},
            {"Mapper.java", "Mapper.java.vm"},
            {"MapperXml.xml", "MapperXml.xml.vm"},
            {"Service.java", "Service.java.vm"},
            {"ServiceImpl.java", "ServiceImpl.java.vm"},
            {"Controller.java", "Controller.java.vm"},
            {"menu.sql", "menu.sql.vm"},
            {"index.html", "index.html.vm"},
    };

    /** 数据库类型 → Java 类型映射 */
    private static final Map<String, String> TYPE_MAPPING = Map.ofEntries(
            Map.entry("bigint", "Long"),
            Map.entry("int", "Integer"),
            Map.entry("integer", "Integer"),
            Map.entry("tinyint", "Integer"),
            Map.entry("smallint", "Integer"),
            Map.entry("mediumint", "Integer"),
            Map.entry("bit", "Boolean"),
            Map.entry("decimal", "java.math.BigDecimal"),
            Map.entry("numeric", "java.math.BigDecimal"),
            Map.entry("float", "Float"),
            Map.entry("double", "Double"),
            Map.entry("datetime", "java.time.LocalDateTime"),
            Map.entry("timestamp", "java.time.LocalDateTime"),
            Map.entry("date", "java.time.LocalDate"),
            Map.entry("time", "java.time.LocalTime"));

    /** 默认 Java 类型（未识别类型兜底） */
    private static final String DEFAULT_JAVA_TYPE = "String";

    /** 数据库类型 → MyBatis jdbcType 映射（mapper xml 使用） */
    private static final Map<String, String> JDBC_TYPE_MAPPING = Map.ofEntries(
            Map.entry("bigint", "BIGINT"),
            Map.entry("int", "INTEGER"),
            Map.entry("integer", "INTEGER"),
            Map.entry("tinyint", "TINYINT"),
            Map.entry("smallint", "SMALLINT"),
            Map.entry("mediumint", "INTEGER"),
            Map.entry("bit", "BOOLEAN"),
            Map.entry("decimal", "DECIMAL"),
            Map.entry("numeric", "DECIMAL"),
            Map.entry("float", "FLOAT"),
            Map.entry("double", "DOUBLE"),
            Map.entry("datetime", "TIMESTAMP"),
            Map.entry("timestamp", "TIMESTAMP"),
            Map.entry("date", "DATE"),
            Map.entry("time", "TIME"));

    private final VelocityEngine velocityEngine;

    @Autowired
    private DataSource dataSource;

    public GenService() {
        // Classpath 资源加载器：模板随 jar 发布（resources/gen/templates/）
        java.util.Properties props = new java.util.Properties();
        props.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        props.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        props.setProperty(RuntimeConstants.INPUT_ENCODING, StandardCharsets.UTF_8.name());
        this.velocityEngine = new VelocityEngine(props);
        this.velocityEngine.init();
    }

    // ====================== 元数据查询 ======================

    /** 数据库内全部表（当前 schema 的 BASE TABLE） */
    public List<Map<String, Object>> listTables() {
        String sql = "SELECT table_name, engine, table_comment, table_rows, create_time "
                + "FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                + "ORDER BY table_name";
        List<Map<String, Object>> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> table = new LinkedHashMap<>();
                table.put("tableName", rs.getString("table_name"));
                table.put("engine", rs.getString("engine"));
                table.put("tableComment", rs.getString("table_comment"));
                table.put("tableRows", rs.getObject("table_rows"));
                table.put("createTime", rs.getObject("create_time"));
                tables.add(table);
            }
        } catch (SQLException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取表清单失败：" + e.getMessage());
        }
        return tables;
    }

    /** 表字段元数据（含主键标记，来自 statistics 索引信息） */
    public List<GenColumn> getColumns(String tableName) {
        validateTableName(tableName);
        List<GenColumn> columns = new ArrayList<>();
        // 主键列名集合（information_schema.statistics）
        java.util.Set<String> pkColumns = new java.util.HashSet<>();
        String pkSql = "SELECT column_name FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = 'PRIMARY'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(pkSql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pkColumns.add(rs.getString("column_name"));
                }
            }
        } catch (SQLException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取主键信息失败：" + e.getMessage());
        }

        String colSql = "SELECT column_name, data_type, column_type, column_comment, is_nullable, column_default "
                + "FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(colSql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GenColumn column = new GenColumn();
                    column.setColumnName(rs.getString("column_name"));
                    String dataType = rs.getString("data_type");
                    column.setDataType(dataType);
                    column.setColumnType(rs.getString("column_type"));
                    column.setColumnComment(rs.getString("column_comment"));
                    String nullable = rs.getString("is_nullable");
                    // 主键与必填字段判断（PRI 或 NOT NULL）
                    column.setPk(pkColumns.contains(column.getColumnName()));
                    column.setRequired("NO".equalsIgnoreCase(nullable) || column.isPk());
                    column.setJavaField(toJavaField(column.getColumnName()));
                    String lowerType = dataType == null ? "" : dataType.toLowerCase();
                    String javaType = TYPE_MAPPING.getOrDefault(lowerType, DEFAULT_JAVA_TYPE);
                    column.setJavaType(javaType);
                    column.setJdbcType(JDBC_TYPE_MAPPING.getOrDefault(lowerType, "VARCHAR"));
                    columns.add(column);
                }
            }
        } catch (SQLException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取字段信息失败：" + e.getMessage());
        }
        if (columns.isEmpty()) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "表不存在或无字段：" + tableName);
        }
        return columns;
    }

    // ====================== 预览与下载 ======================

    /** 预览：输出文件名 → 渲染内容 的有序 Map */
    public Map<String, String> preview(String tableName) {
        GenTableMeta meta = loadMeta(tableName);
        Map<String, String> result = new LinkedHashMap<>();
        for (String[] mapping : TEMPLATE_MAPPINGS) {
            String fileName = meta.getClassName() + mapping[0];
            if ("menu.sql".equals(mapping[0])) {
                fileName = mapping[0];
            } else if ("index.html".equals(mapping[0])) {
                fileName = "index.html";
            }
            result.put(fileName, render(mapping[1], meta));
        }
        return result;
    }

    /** 打包下载：全部生成文件压入一个 zip（UTF-8 文件名） */
    public byte[] downloadZip(String tableName) {
        GenTableMeta meta = loadMeta(tableName);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            String prefix = "moyue-gen-" + meta.getTableName() + "/";
            for (String[] mapping : TEMPLATE_MAPPINGS) {
                String fileName = mapping[0];
                if (!"menu.sql".equals(fileName) && !"index.html".equals(fileName)) {
                    fileName = meta.getClassName() + fileName;
                }
                zip.putNextEntry(new ZipEntry(prefix + fileName));
                zip.write(render(mapping[1], meta).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("[gen] 打包下载失败：{}", tableName, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "生成代码打包失败：" + e.getMessage());
        }
    }

    // ====================== 内部 ======================

    /** 装配 Velocity 渲染上下文 */
    private GenTableMeta loadMeta(String tableName) {
        validateTableName(tableName);
        List<GenColumn> columns = getColumns(tableName);
        GenTableMeta meta = new GenTableMeta();
        meta.setTableName(tableName);
        meta.setClassName(toClassName(tableName));
        meta.setClassNameLower(lowerFirst(meta.getClassName()));
        // 表注释（取第一个非空注释；无注释用表名）
        String comment = queryTableComment(tableName);
        meta.setFunctionName(comment == null || comment.isBlank() ? tableName : comment);
        meta.setPackageName(TARGET_PACKAGE);
        meta.setControllerBase(CONTROLLER_BASE);
        meta.setAuthor("moyue-gen");
        meta.setDateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        GenColumn pk = columns.stream().filter(GenColumn::isPk).findFirst()
                .orElse(columns.get(0));
        meta.setPk(pk);
        meta.setColumns(columns);
        return meta;
    }

    /** Velocity 渲染单个模板 */
    private String render(String templateName, GenTableMeta meta) {
        try {
            Template template = velocityEngine.getTemplate(TEMPLATE_PATH + templateName, StandardCharsets.UTF_8.name());
            VelocityContext context = new VelocityContext();
            context.put("packageName", meta.getPackageName());
            context.put("className", meta.getClassName());
            context.put("classNameLower", meta.getClassNameLower());
            context.put("tableName", meta.getTableName());
            context.put("functionName", meta.getFunctionName());
            context.put("controllerBase", meta.getControllerBase());
            context.put("author", meta.getAuthor());
            context.put("dateTime", meta.getDateTime());
            context.put("pk", meta.getPk());
            context.put("columns", meta.getColumns());
            try (StringWriter writer = new StringWriter()) {
                template.merge(context, writer);
                return writer.toString();
            }
        } catch (Exception e) {
            log.error("[gen] 模板渲染失败：{}", templateName, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "模板渲染失败：" + templateName);
        }
    }

    private String queryTableComment(String tableName) {
        String sql = "SELECT table_comment FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            log.warn("[gen] 读取表注释失败：{}", e.getMessage());
        }
        return null;
    }

    /** 表名白名单校验：仅允许字母数字下划线（防 SQL 注入 information_schema 查询） */
    private void validateTableName(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]{1,64}")) {
            throw new BizException(ResultCode.PARAM_ERROR, "非法表名：" + tableName);
        }
    }

    /** 下划线表名 → 大驼峰类名 */
    private String toClassName(String tableName) {
        String[] parts = tableName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /** 下划线列名 → 小驼峰字段名 */
    private String toJavaField(String columnName) {
        String[] parts = columnName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (i == 0) {
                sb.append(parts[i]);
            } else {
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    private String lowerFirst(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    // ====================== 元数据结构 ======================

    /** 表生成元数据（Velocity 上下文根） */
    @Data
    public static class GenTableMeta {
        private String tableName;
        private String className;
        private String classNameLower;
        private String functionName;
        private String packageName;
        private String controllerBase;
        private String author;
        private String dateTime;
        private GenColumn pk;
        private List<GenColumn> columns;
    }

    /** 列元数据 */
    @Data
    public static class GenColumn {
        private String columnName;
        private String dataType;
        private String columnType;
        private String columnComment;
        private boolean pk;
        private boolean required;
        private String javaField;
        private String javaType;
        private String jdbcType;
    }
}
