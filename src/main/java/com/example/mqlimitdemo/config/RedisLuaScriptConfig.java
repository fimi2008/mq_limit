package com.example.mqlimitdemo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Redis Lua 脚本配置
 * 
 * 将 Lua 脚本从代码中分离到 resources 目录，便于维护和管理
 *
 * @author demo
 */
@Slf4j
@Configuration
public class RedisLuaScriptConfig {

    /**
     * 滑动窗口限流脚本
     * 
     * 脚本位置：resources/lua/sliding_window_rate_limit.lua
     */
    @Bean(name = "slidingWindowScript")
    public DefaultRedisScript<Long> slidingWindowScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/sliding_window_rate_limit.lua")));
        script.setResultType(Long.class);
        log.info("加载 Lua 脚本：滑动窗口限流");
        return script;
    }

    /**
     * 令牌桶限流脚本
     * 
     * 脚本位置：resources/lua/token_bucket_rate_limit.lua
     */
    @Bean(name = "tokenBucketScript")
    public DefaultRedisScript<Long> tokenBucketScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/token_bucket_rate_limit.lua")));
        script.setResultType(Long.class);
        log.info("加载 Lua 脚本：令牌桶限流");
        return script;
    }
}

