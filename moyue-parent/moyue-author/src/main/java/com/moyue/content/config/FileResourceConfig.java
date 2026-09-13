package com.moyue.content.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 本地上传文件的静态资源映射：把 {@code {url-prefix}/**} 映射到磁盘目录 {@code {root}/}。
 * 与 {@code LocalFileStorageService} 使用同一组配置，保证「存进去的路径」能被「访问到」。
 * <p>模块合并说明：原位于 {@code com.moyue.book.config.FileResourceConfig}，随封面上传能力
 * 一并归入内容域；因属「本模块专属的 MVC 扩展」，故放在 {@code com.moyue.content.config} 下。</p>
 */
@Configuration
public class FileResourceConfig implements WebMvcConfigurer {

    @Value("${moyue.storage.local.root:./uploads}")
    private String root;

    @Value("${moyue.storage.local.url-prefix:/api/v1/files}")
    private String urlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations("file:" + root + "/");
    }
}
