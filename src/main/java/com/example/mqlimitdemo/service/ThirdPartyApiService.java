package com.example.mqlimitdemo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模拟第三方接口服务
 * 
 * 该接口有频率限制：每秒最多允许 5 次调用
 *
 * @author demo
 */
@Slf4j
@Service
public class ThirdPartyApiService {

    // 模拟每秒允许的最大调用次数
    private static final int MAX_CALLS_PER_SECOND = 5;
    
    // 当前秒内的调用次数
    private final AtomicInteger callCount = new AtomicInteger(0);
    
    // 上一次重置时间（秒）
    private volatile long lastResetTime = System.currentTimeMillis() / 1000;

    /**
     * 模拟发送消息到第三方接口
     * 
     * @param message 消息内容
     * @return 是否发送成功
     * @throws RateLimitException 如果超过频率限制
     */
    public boolean sendMessage(String message) throws RateLimitException {
        // 检查是否需要重置计数器
        long currentSecond = System.currentTimeMillis() / 1000;
        if (currentSecond > lastResetTime) {
            synchronized (this) {
                if (currentSecond > lastResetTime) {
                    callCount.set(0);
                    lastResetTime = currentSecond;
                    log.debug("计数器已重置，当前秒: {}", currentSecond);
                }
            }
        }

        // 检查是否超过限制
        int currentCount = callCount.incrementAndGet();
        if (currentCount > MAX_CALLS_PER_SECOND) {
            log.warn("⚠️ 超过频率限制！当前调用次数: {}, 限制: {}/秒", currentCount, MAX_CALLS_PER_SECOND);
            throw new RateLimitException("超过第三方接口调用频率限制: " + MAX_CALLS_PER_SECOND + " 次/秒");
        }

        // 模拟调用第三方接口（延迟 100ms）
        try {
            Thread.sleep(100);
            log.info("✅ 第三方接口调用成功 [调用次数: {}/{}] - 消息: {}", 
                    currentCount, MAX_CALLS_PER_SECOND, message);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("第三方接口调用被中断", e);
            return false;
        }
    }

    /**
     * 获取当前剩余调用次数
     */
    public int getRemainingCalls() {
        long currentSecond = System.currentTimeMillis() / 1000;
        if (currentSecond > lastResetTime) {
            return MAX_CALLS_PER_SECOND;
        }
        int remaining = MAX_CALLS_PER_SECOND - callCount.get();
        return Math.max(0, remaining);
    }

    /**
     * 频率限制异常
     */
    public static class RateLimitException extends Exception {
        public RateLimitException(String message) {
            super(message);
        }
    }
}

