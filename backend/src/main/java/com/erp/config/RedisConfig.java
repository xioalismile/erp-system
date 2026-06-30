package com.erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Redis配置
 * Redis连接由Spring Boot自动配置
 * CacheService会自动检测Redis可用性并降级
 */
@Slf4j
@Configuration
public class RedisConfig {
    // Spring Boot自动配置Redis连接
    // CacheService负责检测可用性和降级逻辑
    // 开发模式(dev profile)不需要Redis, 自动用内存替代
}
