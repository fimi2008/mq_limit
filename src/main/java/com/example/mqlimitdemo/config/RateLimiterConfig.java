package com.example.mqlimitdemo.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 限流器配置
 * 
 * 使用 Guava RateLimiter 实现令牌桶算法
 *
 * @author demo
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 创建限流器 Bean
     * 
     * 限制为每秒 5 个令牌（对应第三方接口的限制）
     */
    @Bean(name = "thirdPartyApiRateLimiter")
    public RateLimiter thirdPartyApiRateLimiter() {
        // 每秒允许 5 个请求
        return RateLimiter.create(5.0);
    }
}

