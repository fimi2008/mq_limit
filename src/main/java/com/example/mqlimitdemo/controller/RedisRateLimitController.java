package com.example.mqlimitdemo.controller;

import com.example.mqlimitdemo.limiter.RedisRateLimiter;
import com.example.mqlimitdemo.producer.MessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 分布式限流测试控制器
 *
 * @author demo
 */
@Slf4j
@RestController
@RequestMapping("/redis-rate-limit")
public class RedisRateLimitController {

    @Resource
    private MessageProducer messageProducer;

    @Resource
    private RedisRateLimiter redisRateLimiter;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 测试 Redis 滑动窗口限流
     */
    @GetMapping("/test/sliding-window")
    public Map<String, Object> testSlidingWindow(@RequestParam(defaultValue = "20") int count) {
        log.info("========== 测试 Redis 滑动窗口限流 ==========");
        
        int successCount = 0;
        for (int i = 1; i <= count; i++) {
            try {
                String message = String.format("Redis滑动窗口测试 #%d", i);
                SendResult result = messageProducer.sendSyncMessage("redis-limit-topic", message);
                log.info("消息 #{} 发送成功，MsgId: {}", i, result.getMsgId());
                successCount++;
                Thread.sleep(50);
            } catch (Exception e) {
                log.error("消息 #{} 发送失败: {}", i, e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("algorithm", "滑动窗口");
        response.put("totalCount", count);
        response.put("successCount", successCount);
        response.put("message", "测试完成，请观察消费日志");
        return response;
    }

    /**
     * 测试 Redis 令牌桶限流
     */
    @GetMapping("/test/token-bucket")
    public Map<String, Object> testTokenBucket(@RequestParam(defaultValue = "30") int count) {
        log.info("========== 测试 Redis 令牌桶限流 ==========");
        
        int successCount = 0;
        for (int i = 1; i <= count; i++) {
            try {
                String message = String.format("Redis令牌桶测试 #%d", i);
                messageProducer.sendSyncMessage("redis-limit-topic", message);
                successCount++;
                Thread.sleep(30);
            } catch (Exception e) {
                log.error("消息 #{} 发送失败: {}", i, e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("algorithm", "令牌桶");
        response.put("totalCount", count);
        response.put("successCount", successCount);
        return response;
    }

    /**
     * 测试 Redis 固定窗口限流
     */
    @GetMapping("/test/fixed-window")
    public Map<String, Object> testFixedWindow(@RequestParam(defaultValue = "15") int count) {
        log.info("========== 测试 Redis 固定窗口限流 ==========");
        
        int successCount = 0;
        for (int i = 1; i <= count; i++) {
            try {
                String message = String.format("Redis固定窗口测试 #%d", i);
                messageProducer.sendSyncMessage("redis-limit-topic", message);
                successCount++;
                Thread.sleep(50);
            } catch (Exception e) {
                log.error("消息 #{} 发送失败: {}", i, e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("algorithm", "固定窗口");
        response.put("totalCount", count);
        response.put("successCount", successCount);
        return response;
    }

    /**
     * 直接测试限流器（不通过MQ）
     */
    @GetMapping("/test/direct")
    public Map<String, Object> testDirectRateLimit(
            @RequestParam(defaultValue = "sliding") String algorithm,
            @RequestParam(defaultValue = "10") int count) {
        
        log.info("========== 直接测试 Redis 限流器 [{}] ==========", algorithm);

        int allowedCount = 0;
        int blockedCount = 0;

        for (int i = 1; i <= count; i++) {
            boolean allowed = false;

            switch (algorithm.toLowerCase()) {
                case "sliding":
                    allowed = redisRateLimiter.slidingWindowRateLimit("test_api", 5, 1);
                    break;
                case "token":
                    allowed = redisRateLimiter.tokenBucketRateLimit("test_api", 5, 5);
                    break;
                case "fixed":
                    allowed = redisRateLimiter.fixedWindowRateLimit("test_api", 5, 1);
                    break;
                default:
                    break;
            }

            if (allowed) {
                allowedCount++;
                log.info("请求 #{} - ✅ 通过", i);
            } else {
                blockedCount++;
                log.warn("请求 #{} - ❌ 被限流", i);
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("algorithm", algorithm);
        response.put("totalCount", count);
        response.put("allowedCount", allowedCount);
        response.put("blockedCount", blockedCount);
        response.put("passRate", String.format("%.2f%%", (allowedCount * 100.0 / count)));
        return response;
    }

    /**
     * 获取限流统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats(@RequestParam(defaultValue = "third_party_api") String key) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            long slidingCount = redisRateLimiter.getSlidingWindowCount(key);
            long tokenRemaining = redisRateLimiter.getTokenBucketRemaining(key);
            
            stats.put("key", key);
            stats.put("slidingWindowCount", slidingCount);
            stats.put("tokenBucketRemaining", tokenRemaining);
            stats.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            log.error("获取统计信息失败: {}", e.getMessage());
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 重置限流计数
     */
    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestParam(defaultValue = "third_party_api") String key) {
        try {
            redisRateLimiter.reset(key);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "限流计数已重置: " + key);
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "重置失败: " + e.getMessage());
            return response;
        }
    }

    /**
     * 检查 Redis 连接状态
     */
    @GetMapping("/health")
    public Map<String, Object> checkRedisHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            String pong = stringRedisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            
            health.put("status", "UP");
            health.put("redis", "connected");
            health.put("ping", pong);
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("redis", "disconnected");
            health.put("error", e.getMessage());
        }
        
        return health;
    }
}

