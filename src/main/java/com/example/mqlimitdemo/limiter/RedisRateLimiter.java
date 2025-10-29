package com.example.mqlimitdemo.limiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式限流器
 * 
 * 实现方式：
 * 1. 滑动窗口算法（适合精确限流）
 * 2. 令牌桶算法（适合流量整形）
 * 3. 固定窗口算法（简单高效）
 *
 * @author demo
 */
@Slf4j
@Component
public class RedisRateLimiter {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 滑动窗口限流算法（推荐）
     * 
     * 使用 Redis Sorted Set 实现，score 为时间戳
     * 
     * @param key          限流key
     * @param limit        限流次数
     * @param windowSize   时间窗口大小（秒）
     * @return true-允许通过，false-限流
     */
    public boolean slidingWindowRateLimit(String key, int limit, int windowSize) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - windowSize * 1000L;

        String redisKey = "rate_limit:sliding:" + key;

        try {
            // Lua 脚本保证原子性
            String luaScript = 
                "local key = KEYS[1]\n" +
                "local now = tonumber(ARGV[1])\n" +
                "local windowStart = tonumber(ARGV[2])\n" +
                "local limit = tonumber(ARGV[3])\n" +
                "local windowSize = tonumber(ARGV[4])\n" +
                "\n" +
                "-- 移除窗口外的数据\n" +
                "redis.call('zremrangebyscore', key, 0, windowStart)\n" +
                "\n" +
                "-- 获取当前窗口内的请求数\n" +
                "local current = redis.call('zcard', key)\n" +
                "\n" +
                "if current < limit then\n" +
                "    -- 添加当前请求\n" +
                "    redis.call('zadd', key, now, now)\n" +
                "    -- 设置过期时间\n" +
                "    redis.call('expire', key, windowSize)\n" +
                "    return 1\n" +
                "else\n" +
                "    return 0\n" +
                "end";

            RedisScript<Long> script = RedisScript.of(luaScript, Long.class);
            Long result = stringRedisTemplate.execute(
                script,
                Collections.singletonList(redisKey),
                String.valueOf(now),
                String.valueOf(windowStart),
                String.valueOf(limit),
                String.valueOf(windowSize)
            );

            boolean allowed = result != null && result == 1;
            
            if (allowed) {
                log.debug("✅ 滑动窗口限流通过 - key: {}, limit: {}/{} 秒", key, limit, windowSize);
            } else {
                log.warn("⚠️ 滑动窗口限流拦截 - key: {}, limit: {}/{} 秒", key, limit, windowSize);
            }
            
            return allowed;

        } catch (Exception e) {
            log.error("Redis 限流异常: {}", e.getMessage(), e);
            // 异常时允许通过，避免影响业务（可根据实际情况调整）
            return true;
        }
    }

    /**
     * 令牌桶限流算法
     * 
     * 使用 Redis String 存储令牌数量
     * 
     * @param key          限流key
     * @param limit        桶容量（最大令牌数）
     * @param rate         令牌生成速率（个/秒）
     * @return true-允许通过，false-限流
     */
    public boolean tokenBucketRateLimit(String key, int limit, double rate) {
        String redisKey = "rate_limit:token:" + key;
        long now = Instant.now().toEpochMilli();

        try {
            String luaScript =
                "local key = KEYS[1]\n" +
                "local now = tonumber(ARGV[1])\n" +
                "local limit = tonumber(ARGV[2])\n" +
                "local rate = tonumber(ARGV[3])\n" +
                "\n" +
                "local info = redis.call('hmget', key, 'tokens', 'timestamp')\n" +
                "local tokens = tonumber(info[1])\n" +
                "local timestamp = tonumber(info[2])\n" +
                "\n" +
                "if tokens == nil then\n" +
                "    tokens = limit\n" +
                "    timestamp = now\n" +
                "else\n" +
                "    -- 计算新增的令牌数\n" +
                "    local deltaTime = math.max(0, now - timestamp)\n" +
                "    local newTokens = math.floor(deltaTime * rate / 1000)\n" +
                "    tokens = math.min(limit, tokens + newTokens)\n" +
                "    timestamp = now\n" +
                "end\n" +
                "\n" +
                "if tokens >= 1 then\n" +
                "    tokens = tokens - 1\n" +
                "    redis.call('hmset', key, 'tokens', tokens, 'timestamp', timestamp)\n" +
                "    redis.call('expire', key, 60)\n" +
                "    return 1\n" +
                "else\n" +
                "    return 0\n" +
                "end";

            RedisScript<Long> script = RedisScript.of(luaScript, Long.class);
            Long result = stringRedisTemplate.execute(
                script,
                Collections.singletonList(redisKey),
                String.valueOf(now),
                String.valueOf(limit),
                String.valueOf(rate)
            );

            boolean allowed = result != null && result == 1;
            
            if (allowed) {
                log.debug("✅ 令牌桶限流通过 - key: {}, capacity: {}, rate: {}/秒", key, limit, rate);
            } else {
                log.warn("⚠️ 令牌桶限流拦截 - key: {}, capacity: {}, rate: {}/秒", key, limit, rate);
            }
            
            return allowed;

        } catch (Exception e) {
            log.error("Redis 限流异常: {}", e.getMessage(), e);
            return true;
        }
    }

    /**
     * 固定窗口限流算法（最简单）
     * 
     * 使用 Redis incr 命令实现
     * 
     * @param key          限流key
     * @param limit        限流次数
     * @param windowSize   时间窗口大小（秒）
     * @return true-允许通过，false-限流
     */
    public boolean fixedWindowRateLimit(String key, int limit, int windowSize) {
        // 构建按时间窗口分段的key
        long currentWindow = System.currentTimeMillis() / (windowSize * 1000);
        String redisKey = "rate_limit:fixed:" + key + ":" + currentWindow;

        try {
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            
            if (count == null) {
                return true;
            }

            // 第一次设置过期时间
            if (count == 1) {
                stringRedisTemplate.expire(redisKey, windowSize, TimeUnit.SECONDS);
            }

            boolean allowed = count <= limit;
            
            if (allowed) {
                log.debug("✅ 固定窗口限流通过 - key: {}, count: {}/{}, window: {} 秒", 
                        key, count, limit, windowSize);
            } else {
                log.warn("⚠️ 固定窗口限流拦截 - key: {}, count: {}/{}, window: {} 秒", 
                        key, count, limit, windowSize);
            }
            
            return allowed;

        } catch (Exception e) {
            log.error("Redis 限流异常: {}", e.getMessage(), e);
            return true;
        }
    }

    /**
     * 获取当前限流统计信息（滑动窗口）
     */
    public long getSlidingWindowCount(String key) {
        String redisKey = "rate_limit:sliding:" + key;
        Long count = stringRedisTemplate.opsForZSet().zCard(redisKey);
        return count != null ? count : 0;
    }

    /**
     * 获取当前剩余令牌数（令牌桶）
     */
    public long getTokenBucketRemaining(String key) {
        String redisKey = "rate_limit:token:" + key;
        String tokens = stringRedisTemplate.opsForHash().get(redisKey, "tokens").toString();
        return tokens != null ? Long.parseLong(tokens) : 0;
    }

    /**
     * 重置限流计数
     */
    public void reset(String key) {
        stringRedisTemplate.delete("rate_limit:sliding:" + key);
        stringRedisTemplate.delete("rate_limit:token:" + key);
        log.info("已重置限流计数 - key: {}", key);
    }
}

