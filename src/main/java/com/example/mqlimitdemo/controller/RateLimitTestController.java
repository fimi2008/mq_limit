package com.example.mqlimitdemo.controller;

import com.example.mqlimitdemo.producer.MessageProducer;
import com.example.mqlimitdemo.service.ThirdPartyApiService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 限流测试控制器
 *
 * @author demo
 */
@Slf4j
@RestController
@RequestMapping("/rate-limit")
public class RateLimitTestController {

    @Resource
    private MessageProducer messageProducer;

    @Resource
    private ThirdPartyApiService thirdPartyApiService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 测试基础限流场景
     * 
     * 发送指定数量的消息到限流主题
     */
    @GetMapping("/test/basic")
    public Map<String, Object> testBasicRateLimit(@RequestParam(defaultValue = "20") int count) {
        log.info("========== 开始测试基础限流场景 ==========");
        log.info("将发送 {} 条消息", count);

        int successCount = 0;
        for (int i = 1; i <= count; i++) {
            try {
                String message = String.format("限流测试消息 #%d", i);
                SendResult result = messageProducer.sendSyncMessage("rate-limit-topic", message);
                log.info("消息 #{} 发送成功，MsgId: {}", i, result.getMsgId());
                successCount++;
                
                // 稍微延迟一下，避免生产者发送过快
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("消息 #{} 发送失败: {}", i, e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalCount", count);
        response.put("successCount", successCount);
        response.put("message", String.format("已发送 %d 条消息到 rate-limit-topic", successCount));
        return response;
    }

    /**
     * 测试高级限流场景
     */
    @GetMapping("/test/advanced")
    public Map<String, Object> testAdvancedRateLimit(@RequestParam(defaultValue = "30") int count) {
        log.info("========== 开始测试高级限流场景 ==========");
        log.info("将发送 {} 条消息", count);

        int successCount = 0;
        for (int i = 1; i <= count; i++) {
            try {
                String message = String.format("高级限流测试消息 #%d", i);
                SendResult result = messageProducer.sendSyncMessage("advanced-limit-topic", message);
                log.info("消息 #{} 发送成功，MsgId: {}", i, result.getMsgId());
                successCount++;
                
                Thread.sleep(50);
            } catch (Exception e) {
                log.error("消息 #{} 发送失败: {}", i, e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalCount", count);
        response.put("successCount", successCount);
        response.put("message", String.format("已发送 %d 条消息到 advanced-limit-topic", successCount));
        return response;
    }

    /**
     * 测试直接调用第三方接口（不通过MQ）
     * 
     * 用于对比和验证第三方接口的限流效果
     */
    @GetMapping("/test/direct")
    public Map<String, Object> testDirectCall(@RequestParam(defaultValue = "10") int count) {
        log.info("========== 直接调用第三方接口测试 ==========");

        int successCount = 0;
        int failCount = 0;

        for (int i = 1; i <= count; i++) {
            try {
                String message = String.format("直接调用测试 #%d", i);
                boolean success = thirdPartyApiService.sendMessage(message);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (ThirdPartyApiService.RateLimitException e) {
                log.warn("调用 #{} 触发限流: {}", i, e.getMessage());
                failCount++;
            } catch (Exception e) {
                log.error("调用 #{} 异常: {}", i, e.getMessage());
                failCount++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalCount", count);
        response.put("successCount", successCount);
        response.put("failCount", failCount);
        response.put("message", String.format("完成测试，成功: %d, 失败: %d", successCount, failCount));
        return response;
    }

    /**
     * 测试并发场景
     * 
     * 模拟多个线程同时发送消息
     */
    @GetMapping("/test/concurrent")
    public Map<String, Object> testConcurrent(@RequestParam(defaultValue = "50") int count) {
        log.info("========== 并发测试开始 ==========");

        CompletableFuture<?>[] futures = new CompletableFuture[count];

        for (int i = 0; i < count; i++) {
            int index = i + 1;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    String message = String.format("并发测试消息 #%d - Thread: %s", 
                            index, Thread.currentThread().getName());
                    messageProducer.sendSyncMessage("advanced-limit-topic", message);
                    log.info("消息 #{} 发送成功", index);
                } catch (Exception e) {
                    log.error("消息 #{} 发送失败: {}", index, e.getMessage());
                }
            }, executorService);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures).join();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalCount", count);
        response.put("message", String.format("并发发送 %d 条消息完成", count));
        return response;
    }

    /**
     * 获取第三方接口状态
     */
    @GetMapping("/status")
    public Map<String, Object> getApiStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("remainingCalls", thirdPartyApiService.getRemainingCalls());
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}

