package com.moyue.system.util;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CSV 导出工具。
 *
 * <p>导出需求只在日志域出现，引入 EasyExcel 会显著增大包体，故用轻量实现：
 * 反射读取字段 + UTF-8 BOM（避免 Excel 打开中文乱码）。
 *
 * @author moyue
 */
public final class CsvUtils {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CsvUtils() {
    }

    /**
     * 写出 CSV 文件。
     *
     * @param response HTTP 响应
     * @param filename 文件名（不含扩展名）
     * @param rows     数据行
     * @param fields   需要导出的字段名（与实体属性名一致）
     * @param <T>      行类型
     * @throws IOException 写失败
     */
    public static <T> void write(HttpServletResponse response, String filename,
                                 List<T> rows, String[] fields) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + ".csv");
        try (PrintWriter writer = response.getWriter()) {
            // BOM：Excel 依赖它识别 UTF-8
            writer.write('\ufeff');
            writer.println(String.join(",", fields));
            for (T row : rows) {
                String[] cells = new String[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    cells[i] = escape(readField(row, fields[i]));
                }
                writer.println(String.join(",", cells));
            }
            writer.flush();
        }
    }

    private static Object readField(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            if (value instanceof LocalDateTime time) {
                return FMT.format(time);
            }
            return value;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
