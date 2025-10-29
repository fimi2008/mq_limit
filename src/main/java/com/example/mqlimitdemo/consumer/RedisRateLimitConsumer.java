package com.example.mqlimitdemo.consumer;

import com.example.mqlimitdemo.limiter.RedisRateLimiter;
import com.example.mqlimitdemo.service.ThirdPartyApiService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 基于 Redis 分布式限流的消息消费者
 * 
 * Topic: redis-limit-topic
 * ConsumerGroup: redis-limit-consumer-group
 * 
 * 特点：
 * 1. 使用 Redis 实现分布式限流
 * 2. 多个消费者实例共享限流配置
 * 3. 支持滑动窗口、令牌桶、固定窗口三种算法
 *
 * @author demo
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "redis-limit-topic",
        consumerGroup = "redis-limit-consumer-group",
        // 允许多线程消费，限流由 Redis 控制
        consumeThreadMax = 5
)
public class RedisRateLimitConsumer implements RocketMQListener<String> {

    @Resource
    private RedisRateLimiter redisRateLimiter;

    @Resource
    private ThirdPartyApiService thirdPartyApiService;

    // 限流配置
    private static final String RATE_LIMIT_KEY = "third_party_api";
    private static final int LIMIT = 5; // 每秒最多5次
    private static final int WINDOW_SIZE = 1; // 1秒窗口
    private static final int MAX_RETRY = 3; // 最大重试次数

    @Override
    public void onMessage(String message) {
        log.info("========== Redis 分布式限流消费者 ==========");
        log.info("接收到消息: {}", message);

        // 使用滑动窗口算法限流（推荐）
        boolean success = consumeWithSlidingWindow(message);

        // 或者使用令牌桶算法
        // boolean success = consumeWithTokenBucket(message);

        // 或者使用固定窗口算法
        // boolean success = consumeWithFixedWindow(message);

        if (!success) {
            log.error("消息消费失败，等待重试");
            throw new RuntimeException("消息消费失败");
        }

        log.info("========== 消息消费成功 ==========\n");
    }

    /**
     * 方案1：滑动窗口算法（推荐）
     * 
     * 优点：精确限流，没有临界问题
     * 缺点：需要存储窗口内所有请求记录
     */
    private boolean consumeWithSlidingWindow(String message) {
        int retryCount = 0;

        while (retryCount < MAX_RETRY) {
            // 尝试获取限流许可
            boolean allowed = redisRateLimiter.slidingWindowRateLimit(
                    RATE_LIMIT_KEY, 
                    LIMIT, 
                    WINDOW_SIZE
            );

            if (allowed) {
                try {
                    // 调用第三方接口
                    boolean result = thirdPartyApiService.sendMessage(message);
                    if (result) {
                        log.info("✅ 消息处理成功（滑动窗口）");
                        return true;
                    }
                } catch (ThirdPartyApiService.RateLimitException e) {
                    log.warn("第三方接口限流，等待后重试...");
                } catch (Exception e) {
                    log.error("调用第三方接口异常: {}", e.getMessage());
                }
            } else {
                log.warn("⚠️ Redis 限流拦截，第 {} 次重试，等待 {} 毫秒...", 
                        retryCount + 1, 200);
            }

            // 等待后重试
            retryCount++;
            if (retryCount < MAX_RETRY) {
                try {
                    Thread.sleep(200); // 等待 200ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("❌ 重试 {} 次后仍然失败", MAX_RETRY);
        return false;
    }

    /**
     * 方案2：令牌桶算法
     * 
     * 优点：支持突发流量，流量整形效果好
     * 缺点：实现相对复杂
     */
    private boolean consumeWithTokenBucket(String message) {
        int retryCount = 0;

        while (retryCount < MAX_RETRY) {
            // 尝试获取令牌
            boolean allowed = redisRateLimiter.tokenBucketRateLimit(
                    RATE_LIMIT_KEY,
                    LIMIT,      // 桶容量
                    LIMIT       // 令牌生成速率（每秒）
            );

            if (allowed) {
                try {
                    boolean result = thirdPartyApiService.sendMessage(message);
                    if (result) {
                        log.info("✅ 消息处理成功（令牌桶）");
                        return true;
                    }
                } catch (Exception e) {
                    log.error("调用第三方接口异常: {}", e.getMessage());
                }
            }

            retryCount++;
            if (retryCount < MAX_RETRY) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return false;
    }

    /**
     * 方案3：固定窗口算法（最简单）
     * 
     * 优点：实现简单，性能最好
     * 缺点：有临界问题（窗口边界可能瞬间超限）
     */
    private boolean consumeWithFixedWindow(String message) {
        // 尝试获取限流许可
        boolean allowed = redisRateLimiter.fixedWindowRateLimit(
                RATE_LIMIT_KEY,
                LIMIT,
                WINDOW_SIZE
        );

        if (!allowed) {
            log.warn("⚠️ 固定窗口限流拦截");
            return false;
        }

        try {
            boolean result = thirdPartyApiService.sendMessage(message);
            if (result) {
                log.info("✅ 消息处理成功（固定窗口）");
                return true;
            }
        } catch (Exception e) {
            log.error("调用第三方接口异常: {}", e.getMessage());
        }

        return false;
    }
}

