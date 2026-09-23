package com.moyue;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 墨阅小说网 · 单体服务入口。
 *
 * <p>完全拍平后的单体：所有业务包（account / auth / ai / content / commerce / social / system ...
 * ）同处 {@code com.moyue} 包下，由统一组件扫描加载；Mapper 由 MyBatis-Plus 扫描。
 * 不再使用 Spring Cloud / OpenFeign / Nacos 注册发现 / Gateway，跨模块调用改为进程内 Service 注入。
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan(
    value = "com.moyue",
    annotationClass = Mapper.class,
    markerInterface = BaseMapper.class
)
public class MoyueApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoyueApplication.class, args);
    }
}
