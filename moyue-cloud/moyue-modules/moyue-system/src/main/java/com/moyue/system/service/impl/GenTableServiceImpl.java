package com.moyue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.system.domain.entity.GenTable;
import com.moyue.system.domain.entity.GenTableColumn;
import com.moyue.system.mapper.GenTableColumnMapper;
import com.moyue.system.mapper.GenTableMapper;
import com.moyue.system.service.GenTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成域（⑮）实现。
 *
 * <p>链路：information_schema 导入表结构 → 生成配置（gen_table / gen_table_column）
 * → Velocity 渲染模板 → Zip 下载或写入本地路径。
 *
 * <p><b>安全边界</b>：写入本地路径（gen_type = 1）仅允许 dev 环境，生产环境调用直接拒绝
 * （架构说明书 12.1 D-19）。生成结果不自动写入版本库，需开发检视后提交。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenTableServiceImpl extends ServiceImpl<GenTableMapper, GenTable> implements GenTableService {

    /** 需要跳过的内置列（审计与逻辑删除列由 BaseEntity 统一承载） */
    private static final List<String> SKIP_COLUMNS = List.of(
            "id", "create_by", "create_time", "update_by", "update_time", "remark", "is_deleted");

    /** 模板清单：模板路径 -> 输出文件相对路径 */
    private static final Map<String, String> TEMPLATES = Map.of(
            "vm/java/Entity.java.vm", "java/{packagePath}/domain/entity/{ClassName}.java",
            "vm/java/Controller.java.vm", "java/{packagePath}/controller/{ClassName}Controller.java",
            "vm/java/Service.java.vm", "java/{packagePath}/service/{ClassName}Service.java",
            "vm/vue/index.vue.vm", "vue/{businessName}/index.vue",
            "vm/ts/api.ts.vm", "ts/api/{businessName}.ts");

    private final GenTableColumnMapper columnMapper;
    private final ObjectMapper objectMapper;
    private final VelocityEngine velocityEngine;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private VelocityEngine engine() {
        return velocityEngine;
    }

    @Override
    public List<Map<String, Object>> listDbTables(String tableName) {
        List<Map<String, Object>> rows = baseMapper.selectDbTables(tableName, "moyue");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = String.valueOf(row.get("tableName"));
            Long count = lambdaQuery().eq(GenTable::getTableName, name).count();
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("imported", count != null && count > 0);
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importTables(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要导入的表");
        }
        int n = 0;
        for (String tableName : tableNames) {
            Long exist = lambdaQuery().eq(GenTable::getTableName, tableName).count();
            if (exist != null && exist > 0) {
                continue;
            }
            GenTable table = buildTableFromSchema(tableName);
            save(table);
            for (GenTableColumn column : buildColumnsFromSchema(tableName, table.getId())) {
                columnMapper.insert(column);
            }
            n++;
        }
        return n;
    }

    @Override
    public GenTable detail(Long tableId) {
        GenTable table = getById(tableId);
        if (table == null) {
            throw BusinessException.notFound("生成配置");
        }
        return table;
    }

    @Override
    public List<GenTableColumn> listColumns(Long tableId) {
        return columnMapper.selectList(new LambdaQueryWrapper<GenTableColumn>()
                .eq(GenTableColumn::getTableId, tableId)
                .orderByAsc(GenTableColumn::getSort));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateConfig(GenTable table, List<GenTableColumn> columns) {
        if (getById(table.getId()) == null) {
            throw BusinessException.notFound("生成配置");
        }
        updateById(table);
        if (columns != null) {
            for (GenTableColumn column : columns) {
                if (column.getId() == null) {
                    column.setTableId(table.getId());
                    columnMapper.insert(column);
                } else {
                    columnMapper.updateById(column);
                }
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConfig(Long tableId) {
        if (getById(tableId) == null) {
            throw BusinessException.notFound("生成配置");
        }
        columnMapper.delete(new LambdaQueryWrapper<GenTableColumn>().eq(GenTableColumn::getTableId, tableId));
        return removeById(tableId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncTable(Long tableId) {
        GenTable table = getById(tableId);
        if (table == null) {
            throw BusinessException.notFound("生成配置");
        }
        List<GenTableColumn> dbColumns = buildColumnsFromSchema(table.getTableName(), tableId);
        List<GenTableColumn> existColumns = listColumns(tableId);
        Map<String, GenTableColumn> existMap = new LinkedHashMap<>();
        existColumns.forEach(c -> existMap.put(c.getColumnName(), c));

        int n = 0;
        for (GenTableColumn dbColumn : dbColumns) {
            GenTableColumn exist = existMap.get(dbColumn.getColumnName());
            if (exist == null) {
                columnMapper.insert(dbColumn);
                n++;
            } else if (!equalsIgnoreNull(dbColumn, exist)) {
                dbColumn.setId(exist.getId());
                columnMapper.updateById(dbColumn);
                n++;
            }
        }
        // 数据库中已删除的列，同步移除生成配置
        List<String> dbColumnNames = dbColumns.stream().map(GenTableColumn::getColumnName).toList();
        for (GenTableColumn exist : existColumns) {
            if (!dbColumnNames.contains(exist.getColumnName())) {
                columnMapper.deleteById(exist.getId());
                n++;
            }
        }
        return n;
    }

    @Override
    public Map<String, String> preview(Long tableId) {
        GenTable table = getById(tableId);
        if (table == null) {
            throw BusinessException.notFound("生成配置");
        }
        Map<String, String> files = new LinkedHashMap<>();
        VelocityContext context = buildContext(table);
        TEMPLATES.forEach((tpl, out) ->
                files.put(resolvePath(out, table), render(tpl, context)));
        return files;
    }

    @Override
    public byte[] download(Long tableId) {
        Map<String, String> files = preview(tableId);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (Exception e) {
            log.error("代码打包失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "代码打包失败");
        }
        return bos.toByteArray();
    }

    @Override
    public List<String> generateToPath(Long tableId, String genPath) {
        // 安全边界：仅 dev 环境允许写本地路径
        if (!"dev".equalsIgnoreCase(activeProfile)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "生成到本地路径仅允许 dev 环境，当前环境：" + activeProfile);
        }
        String root = StringUtils.isBlank(genPath) ? System.getProperty("user.dir") : genPath;
        Map<String, String> files = preview(tableId);
        List<String> written = new ArrayList<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            try {
                Path target = Path.of(root, entry.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
                written.add(target.toString());
            } catch (Exception e) {
                log.warn("写入文件失败 {}：{}", entry.getKey(), e.getMessage());
            }
        }
        return written;
    }

    // ---------------------------------------------------------- 内部方法

    private GenTable buildTableFromSchema(String tableName) {
        Map<String, Object> tableInfo = baseMapper.selectTableByName(tableName, "moyue");
        if (tableInfo == null) {
            throw BusinessException.notFound("数据表 " + tableName);
        }
        GenTable table = new GenTable();
        table.setTableName(tableName);
        table.setTableComment(String.valueOf(tableInfo.getOrDefault("tableComment", "")));
        String className = toClassName(tableName);
        table.setClassName(className);
        table.setTplCategory("crud");
        table.setPackageName("com.moyue." + moduleNameOf(tableName));
        table.setModuleName(moduleNameOf(tableName));
        table.setBusinessName(toCamel(tableName.replaceFirst("^[a-z]+_", "")));
        table.setFunctionName(String.valueOf(tableInfo.getOrDefault("tableComment", tableName)));
        table.setFunctionAuthor("moyue");
        table.setGenType(0);
        table.setGenPath("/");
        return table;
    }

    private List<GenTableColumn> buildColumnsFromSchema(String tableName, Long tableId) {
        List<Map<String, Object>> columns = baseMapper.selectTableColumns(tableName, "moyue");
        List<GenTableColumn> list = new ArrayList<>();
        int sort = 1;
        for (Map<String, Object> column : columns) {
            String name = String.valueOf(column.get("columnName"));
            if (SKIP_COLUMNS.contains(name)) {
                continue;
            }
            String dataType = String.valueOf(column.get("dataType"));
            String comment = String.valueOf(column.getOrDefault("columnComment", ""));
            GenTableColumn gc = new GenTableColumn();
            gc.setTableId(tableId);
            gc.setColumnName(name);
            gc.setColumnComment(comment);
            gc.setColumnType(String.valueOf(column.getOrDefault("columnType", "")));
            gc.setJavaField(toCamel(name));
            gc.setJavaType(javaTypeOf(dataType));
            gc.setIsPk("PRI".equals(String.valueOf(column.get("columnKey"))) ? 1 : 0);
            gc.setIsIncrement("auto_increment".equals(String.valueOf(column.getOrDefault("extra", ""))) ? 1 : 0);
            gc.setIsRequired("NO".equals(String.valueOf(column.get("isNullable"))) ? 1 : 0);
            gc.setIsInsert(1);
            gc.setIsEdit(gc.getIsPk() == 1 ? 0 : 1);
            gc.setIsList(1);
            gc.setIsQuery(0);
            gc.setQueryType("EQ");
            gc.setHtmlType(htmlTypeOf(dataType, gc.getColumnType()));
            gc.setDictType("");
            gc.setSort(sort++);
            list.add(gc);
        }
        return list;
    }

    private VelocityContext buildContext(GenTable table) {
        VelocityContext context = new VelocityContext();
        context.put("table", table);
        context.put("columns", listColumns(table.getId()));
        context.put("ClassName", table.getClassName());
        context.put("className", lowerFirst(table.getClassName()));
        context.put("packageName", table.getPackageName());
        context.put("packagePath", table.getPackageName().replace('.', '/'));
        context.put("moduleName", table.getModuleName());
        context.put("businessName", table.getBusinessName());
        context.put("functionName", table.getFunctionName());
        context.put("author", table.getFunctionAuthor());
        context.put("date", LocalDate.now().toString());
        context.put("pkColumn", listColumns(table.getId()).stream()
                .filter(c -> c.getIsPk() != null && c.getIsPk() == 1)
                .findFirst().orElse(null));
        return context;
    }

    private String render(String tpl, VelocityContext context) {
        try {
            Template template = engine().getTemplate(tpl, "UTF-8");
            StringWriter writer = new StringWriter();
            template.merge(context, writer);
            return writer.toString();
        } catch (Exception e) {
            log.error("模板渲染失败 {}", tpl, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板渲染失败：" + tpl);
        }
    }

    private String resolvePath(String pattern, GenTable table) {
        return pattern
                .replace("{packagePath}", table.getPackageName().replace('.', '/'))
                .replace("{ClassName}", table.getClassName())
                .replace("{businessName}", table.getBusinessName());
    }

    private boolean equalsIgnoreNull(GenTableColumn a, GenTableColumn b) {
        return java.util.Objects.equals(nvl(a.getColumnType()), nvl(b.getColumnType()))
                && java.util.Objects.equals(nvl(a.getColumnComment()), nvl(b.getColumnComment()))
                && java.util.Objects.equals(nvl(a.getJavaType()), nvl(b.getJavaType()));
    }

    private static String nvl(String v) {
        return v == null ? "" : v;
    }

    private static String toClassName(String tableName) {
        // sys_user_role -> SysUserRole（去掉首字母前缀段）
        String name = tableName.contains("_")
                ? tableName.substring(tableName.indexOf('_') + 1) : tableName;
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("_")) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String toCamel(String name) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String lowerFirst(String s) {
        return s == null || s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String moduleNameOf(String tableName) {
        return tableName.contains("_") ? tableName.substring(0, tableName.indexOf('_')) : tableName;
    }

    private static String javaTypeOf(String dataType) {
        String type = dataType.toLowerCase();
        if (type.contains("bigint")) {
            return "Long";
        }
        if (type.contains("int") || type.contains("tinyint")) {
            return "Integer";
        }
        if (type.contains("decimal") || type.contains("numeric")) {
            return "java.math.BigDecimal";
        }
        if (type.contains("datetime") || type.contains("timestamp")) {
            return "java.time.LocalDateTime";
        }
        if (type.contains("date")) {
            return "java.time.LocalDate";
        }
        return "String";
    }

    private static String htmlTypeOf(String dataType, String columnType) {
        String type = dataType.toLowerCase();
        if (type.contains("text") || (columnType != null && columnType.contains("text"))) {
            return "textarea";
        }
        if (type.contains("datetime") || type.contains("timestamp")) {
            return "datetime";
        }
        if (type.contains("int")) {
            return "number";
        }
        return "input";
    }
}
