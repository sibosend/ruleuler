package com.caritasem.ruleuler.server.auth;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 统一的 auth 模块测试配置，扫描整个 com.caritasem.ruleuler.server.auth 包。
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = {"com.caritasem.ruleuler.server.auth", "com.caritasem.ruleuler.server.audit"})
public class AuthTestConfig {
}
