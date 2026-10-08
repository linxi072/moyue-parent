package com.moyue.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 单体启动器（8080，仅 boot 仓）。
 *
 * <p><b>与 cloud 形态的三处差异，全部收口在本模块</b>：<ol>
 *   <li><b>跨模块调用</b>：不启用 Feign（无 {@code @EnableFeignClients}），由
 *       {@code adapter} 包下的 Local 实现直接注入 Service；</li>
 *   <li><b>身份注入</b>：没有网关，改由 {@code BootAuthFilter} 在进程内完成
 *       「解析 JWT → 写 UserContext → 传递身份头」；</li>
 *   <li><b>配置来源</b>：不连 Nacos，配置走本地 application.yml / 环境变量。</li>
 * </ol>
 *
 * <p><b>为什么要排除各模块的 XxxApplication</b>：组件扫描会扫到它们，而它们身上的
 * {@code @EnableDiscoveryClient} / {@code @EnableFeignClients} 会把单体进程重新
 * 拉回微服务形态（去连注册中心、为 Feign 接口建代理）。用正则排除所有
 * {@code com.moyue.<模块>.XxxApplication} 启动类，是单体聚合的通行做法。
 *
 * @author moyue
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.moyue", excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.moyue\\.[a-z]+[a-zA-Z]*\\.[A-Z][A-Za-z]*Application")
})
@MapperScan("com.moyue.**.mapper")
public class BootApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootApplication.class, args);
    }
}
