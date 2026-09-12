package com.moyue.book.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象：屏蔽底层存储介质，业务层只关心「给我文件，还我可访问 URL」。
 * <p>当前提供 {@link LocalFileStorageService} 本地磁盘实现（开发 / 演示环境）。
 * 生产环境替换为 OSS / MinIO / COS 实现即可，接口签名不变，调用方零改动。</p>
 */
public interface FileStorage {

    /**
     * 保存上传文件并返回可访问 URL。
     *
     * @param file     上传文件
     * @param category 业务目录（如 covers / avatars），用于隔离不同业务的文件
     * @return 可访问 URL
     */
    String store(MultipartFile file, String category);
}
