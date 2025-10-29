package com.example.mqlimitdemo.consumer;

import com.example.mqlimitdemo.service.ThirdPartyApiService;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 高级限流消息消费者 - 使用多种策略
 * 
 * Topic: advanced-limit-topic
 * ConsumerGroup: advanced-limit-consumer-group
 * 
 * 演示多种限流处理策略：
 * 1. 令牌桶限流
 * 2. 降级处理
 * 3. 延迟重试
 * 4. 熔断机制
 *
 * @author demo
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "advanced-limit-topic",
        consumerGroup = "advanced-limit-consumer-group",
        consumeThreadMax = 3 // 允许 3 个线程并发消费
)
public class AdvancedRateLimitConsumer implements RocketMQListener<String> {

    @Resource(name = "thirdPartyApiRateLimiter")
    private RateLimiter rateLimiter;

    @Resource
    private ThirdPartyApiService thirdPartyApiService;

    // 熔断计数器
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    private int consecutiveFailures = 0;
    private long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_RESET_TIME = 10000; // 10秒后尝试恢复

    @Override
    public void onMessage(String message) {
        log.info("========== 高级限流消费者 ==========");
        log.info("接收到消息: {}", message);

        // 检查熔断器状态
        if (isCircuitBreakerOpen()) {
            log.warn("⚠️ 熔断器已打开，消息将被延迟处理");
            // 可以将消息放入延迟队列，或者抛出异常让MQ稍后重试
            throw new RuntimeException("熔断器打开，消息延迟处理");
        }

        try {
            // 尝试获取令牌（最多等待 3 秒）
            boolean acquired = rateLimiter.tryAcquire(3, TimeUnit.SECONDS);
            
            if (!acquired) {
                log.warn("⚠️ 无法获取令牌，触发降级处理");
                handleDegradation(message);
                return;
            }

            // 调用第三方接口
            boolean success = thirdPartyApiService.sendMessage(message);
            
            if (success) {
                // 成功则重置失败计数
                resetCircuitBreaker();
                log.info("✅ 消息处理成功");
            } else {
                handleFailure(message);
            }

        } catch (ThirdPartyApiService.RateLimitException e) {
            log.error("❌ 触发第三方接口限流: {}", e.getMessage());
            handleFailure(message);
            throw new RuntimeException("频率限制，等待重试", e);
            
        } catch (Exception e) {
            log.error("消息处理异常: {}", e.getMessage(), e);
            handleFailure(message);
            throw new RuntimeException("消息处理失败", e);
        }

        log.info("====================================\n");
    }

    /**
     * 检查熔断器是否打开
     */
    private boolean isCircuitBreakerOpen() {
        if (circuitBreakerOpenTime > 0) {
            long elapsed = System.currentTimeMillis() - circuitBreakerOpenTime;
            if (elapsed > CIRCUIT_BREAKER_RESET_TIME) {
                // 尝试恢复
                log.info("🔄 熔断器恢复，尝试重新处理消息");
                circuitBreakerOpenTime = 0;
                consecutiveFailures = 0;
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 处理失败情况
     */
    private void handleFailure(String message) {
        consecutiveFailures++;
        log.warn("连续失败次数: {}/{}", consecutiveFailures, CIRCUIT_BREAKER_THRESHOLD);

        if (consecutiveFailures >= CIRCUIT_BREAKER_THRESHOLD) {
            // 打开熔断器
            circuitBreakerOpenTime = System.currentTimeMillis();
            log.error("🔴 熔断器已打开！连续失败 {} 次", consecutiveFailures);
        }
    }

    /**
     * 重置熔断器
     */
    private void resetCircuitBreaker() {
        if (consecutiveFailures > 0) {
            log.info("✅ 重置熔断器，连续失败次数: {} -> 0", consecutiveFailures);
            consecutiveFailures = 0;
            circuitBreakerOpenTime = 0;
        }
    }

    /**
     * 降级处理
     * 
     * 当无法正常处理时，采用降级策略：
     * 1. 记录到本地队列，稍后重试
     * 2. 发送到备用通道
     * 3. 仅记录日志，不调用第三方接口
     */
    private void handleDegradation(String message) {
        log.warn("🔻 执行降级处理 - 消息: {}", message);
        
        // 降级策略：将消息记录到日志，等待后续处理
        log.info("降级策略：消息已记录，等待系统恢复后重新处理");
        
        // 实际场景可以：
        // 1. 保存到数据库
        // 2. 写入本地文件
        // 3. 发送到备用队列
        // 4. 发送告警通知
        
        // 这里抛出异常，让 RocketMQ 稍后重试
        throw new RuntimeException("系统繁忙，触发降级处理");
    }
}

