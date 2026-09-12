package com.moyue.book.service;

import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘文件存储实现（开发 / 演示环境）。
 * <p>落盘相对路径：{@code {category}/{yyyyMMdd}/{UUID}.{ext}}，避免单目录文件过多与文件名冲突。
 * 对外 URL 由 {@code moyue.storage.local.url-prefix} + 相对路径拼装，
 * 静态资源映射见 {@code com.moyue.content.config.FileResourceConfig}。</p>
 * <p><b>生产环境替换为 OSS / MinIO 实现即可，{@link FileStorage} 接口不变，调用方零改动。</b></p>
 */
@Service
public class LocalFileStorageService implements FileStorage {

    /** 允许的扩展名白名单 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /** 图片 contentType 前缀 */
    private static final String IMAGE_PREFIX = "image/";

    /** 日期目录格式：yyyyMMdd */
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 存储根目录 */
    @Value("${moyue.storage.local.root:./uploads}")
    private String root;

    /** 对外访问 URL 前缀 */
    @Value("${moyue.storage.local.url-prefix:/api/v1/files}")
    private String urlPrefix;

    /** 单文件大小上限（MB） */
    @Value("${moyue.storage.max-size-mb:5}")
    private int maxSizeMb;

    @Override
    public String store(MultipartFile file, String category) {
        // 1) 空文件
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "请选择要上传的文件");
        }
        // 2) 大小校验
        long maxBytes = maxSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BizException(ResultCode.PARAM_ERROR, "封面大小不能超过 " + maxSizeMb + " MB");
        }
        // 3) 扩展名白名单（取原始后缀小写；无后缀时用 contentType 推断，仍无法判定则拒）
        String ext = resolveExt(file);
        if (ext == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "仅支持 jpg/png/webp/gif 格式");
        }

        String dir = (category == null || category.isBlank()) ? "common" : category.trim();
        String relative = dir + "/" + LocalDate.now().format(DATE_DIR) + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path target = Paths.get(root).resolve(relative).normalize();
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件上传失败，请稍后重试");
        }
        // Windows 分隔符统一换成 /，保证返回的是合法 URL 路径
        return urlPrefix + "/" + relative.replace("\\", "/");
    }

    /** 推断扩展名：原始文件名后缀优先，无后缀时用 contentType；不在白名单返回 null */
    private String resolveExt(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            return ALLOWED_EXT.contains(ext) ? ext : null;
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith(IMAGE_PREFIX)) {
            String ext = contentType.substring(IMAGE_PREFIX.length()).toLowerCase(Locale.ROOT);
            return ALLOWED_EXT.contains(ext) ? ext : null;
        }
        return null;
    }
}
