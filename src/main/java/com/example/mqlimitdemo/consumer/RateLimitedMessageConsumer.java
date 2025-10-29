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
 * 带限流的消息消费者
 * 
 * Topic: rate-limit-topic
 * ConsumerGroup: rate-limit-consumer-group
 * 
 * 该消费者演示如何处理第三方接口的频率限制
 *
 * @author demo
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "rate-limit-topic",
        consumerGroup = "rate-limit-consumer-group",
        // 设置单线程消费，避免并发导致限流问题
        consumeThreadMax = 1
)
public class RateLimitedMessageConsumer implements RocketMQListener<String> {

    @Resource(name = "thirdPartyApiRateLimiter")
    private RateLimiter rateLimiter;

    @Resource
    private ThirdPartyApiService thirdPartyApiService;

    // 最大重试次数
    private static final int MAX_RETRY_TIMES = 3;

    @Override
    public void onMessage(String message) {
        log.info("========== 开始消费消息（带限流） ==========");
        log.info("接收到消息: {}", message);

        // 方案1：使用 RateLimiter 控制消费速度
        boolean success = consumeWithRateLimiter(message);

        if (!success) {
            // 如果失败，尝试重试机制
            log.error("消息消费失败，将进行重试");
            throw new RuntimeException("消息消费失败");
        }

        log.info("========== 消息消费成功 ==========\n");
    }

    /**
     * 方案1：使用 RateLimiter 控制调用频率
     */
    private boolean consumeWithRateLimiter(String message) {
        try {
            // 获取令牌，如果获取不到会阻塞等待
            log.info("正在获取令牌...");
            double waitTime = rateLimiter.acquire();
            log.info("成功获取令牌，等待时间: {} 秒", String.format("%.2f", waitTime));

            // 调用第三方接口
            boolean result = thirdPartyApiService.sendMessage(message);
            log.info("第三方接口调用结果: {}", result ? "成功" : "失败");
            return result;

        } catch (ThirdPartyApiService.RateLimitException e) {
            log.error("触发第三方接口频率限制: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("调用第三方接口异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 方案2：尝试获取令牌，如果获取不到则等待后重试
     */
    private boolean consumeWithRetry(String message) {
        for (int i = 0; i < MAX_RETRY_TIMES; i++) {
            try {
                // 尝试在 2 秒内获取令牌
                boolean acquired = rateLimiter.tryAcquire(2, TimeUnit.SECONDS);
                if (!acquired) {
                    log.warn("第 {} 次尝试：未能获取到令牌，继续等待...", i + 1);
                    continue;
                }

                log.info("第 {} 次尝试：成功获取令牌", i + 1);

                // 调用第三方接口
                boolean result = thirdPartyApiService.sendMessage(message);
                if (result) {
                    log.info("消息发送成功");
                    return true;
                }

            } catch (ThirdPartyApiService.RateLimitException e) {
                log.warn("第 {} 次尝试：触发频率限制，等待后重试...", i + 1);
                try {
                    Thread.sleep(1000); // 等待 1 秒后重试
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                log.error("第 {} 次尝试失败: {}", i + 1, e.getMessage());
            }
        }

        log.error("重试 {} 次后仍然失败", MAX_RETRY_TIMES);
        return false;
    }
}

