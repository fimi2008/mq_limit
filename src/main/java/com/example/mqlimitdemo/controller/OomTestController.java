package com.example.mqlimitdemo.controller;

import com.example.mqlimitdemo.producer.MessageProducer;
import com.example.mqlimitdemo.service.MemoryLeakService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * OutOfMemoryError 测试控制器
 * 
 * 用于演示和控制 Java heap space OOM 场景
 * 
 * 核心接口：
 * 1. POST /oom/start - 启动内存泄漏模式
 * 2. POST /oom/stop - 停止内存泄漏模式
 * 3. POST /oom/clear - 清理累积的内存
 * 4. GET  /oom/stats - 查看内存统计信息
 * 5. POST /oom/send - 发送OOM测试消息
 * 6. POST /oom/send-batch - 批量发送消息（加速OOM）
 * 
 * 典型使用流程：
 * 1. 先启动内存泄漏模式：POST /oom/start?sizeKB=100
 * 2. 发送测试消息：POST /oom/send-batch?count=100
 * 3. 监控内存状态：GET /oom/stats
 * 4. 重复步骤2-3，观察内存缓慢增长
 * 5. 最终会触发 OutOfMemoryError
 * 
 * 内存配置建议：
 * - 为了快速看到效果，建议限制 JVM 堆内存，例如：-Xmx256m -Xms128m
 * - 在 IDEA 中配置：Run -> Edit Configurations -> VM options: -Xmx256m -Xms128m
 * 
 * ⚠️ 警告：此功能会导致真实的内存溢出，仅在测试环境使用！
 *
 * @author demo
 */
@Slf4j
@RestController
@RequestMapping("/oom")
public class OomTestController {

    @Resource
    private MemoryLeakService memoryLeakService;

    @Resource
    private MessageProducer messageProducer;

    /**
     * 启动内存泄漏模式
     * 
     * @param sizeKB 每个消息累积的对象大小（KB），默认100KB
     * @return 结果
     */
    @PostMapping("/start")
    public Map<String, Object> startMemoryLeak(
            @RequestParam(defaultValue = "100") int sizeKB) {
        
        log.warn("⚠️ 准备启动内存泄漏模式，每个消息将累积 {} KB 数据", sizeKB);
        memoryLeakService.startMemoryLeak(sizeKB);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "内存泄漏模式已启动");
        response.put("objectSizeKB", sizeKB);
        response.put("warning", "⚠️ 这将导致真实的内存溢出！");
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }

    /**
     * 停止内存泄漏模式（不清理已累积的内存）
     * 
     * @return 结果
     */
    @PostMapping("/stop")
    public Map<String, Object> stopMemoryLeak() {
        memoryLeakService.stopMemoryLeak();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "内存泄漏模式已停止（已累积内存未清理）");
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }

    /**
     * 清理所有累积的内存数据
     * 
     * @return 结果
     */
    @PostMapping("/clear")
    public Map<String, Object> clearMemory() {
        long beforeCount = memoryLeakService.getMessageCount();
        int beforeCacheSize = memoryLeakService.getCacheSize();
        
        memoryLeakService.clearMemory();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "已清理所有累积的内存数据");
        response.put("clearedMessageCount", beforeCount);
        response.put("clearedCacheSize", beforeCacheSize);
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }

    /**
     * 获取内存统计信息
     * 
     * @return 内存统计数据
     */
    @GetMapping("/stats")
    public Map<String, Object> getMemoryStats() {
        Map<String, Object> stats = memoryLeakService.getMemoryStats();
        stats.put("success", true);
        stats.put("timestamp", System.currentTimeMillis());
        
        // 打印到日志
        memoryLeakService.logMemoryStats();
        
        return stats;
    }

    /**
     * 发送单条OOM测试消息
     * 
     * @param message 消息内容
     * @return 结果
     */
    @PostMapping("/send")
    public Map<String, Object> sendOomTestMessage(
            @RequestParam(defaultValue = "OOM Test Message") String message) {
        
        if (!memoryLeakService.isLeakEnabled()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "内存泄漏模式未启动，请先调用 POST /oom/start");
            response.put("timestamp", System.currentTimeMillis());
            return response;
        }
        
        SendResult result = messageProducer.sendSyncMessage("oom-test-topic", message);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "OOM测试消息发送成功");
        response.put("msgId", result.getMsgId());
        response.put("sendStatus", result.getSendStatus());
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }

    /**
     * 批量发送OOM测试消息
     * 
     * @param count 发送数量，默认50
     * @return 结果
     */
    @PostMapping("/send-batch")
    public Map<String, Object> sendBatchOomTestMessage(
            @RequestParam(defaultValue = "50") int count) {
        
        if (!memoryLeakService.isLeakEnabled()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "内存泄漏模式未启动，请先调用 POST /oom/start");
            response.put("timestamp", System.currentTimeMillis());
            return response;
        }
        
        log.info("批量发送 {} 条OOM测试消息", count);
        
        int successCount = 0;
        int failCount = 0;
        
        for (int i = 0; i < count; i++) {
            try {
                String message = "OOM Test Batch Message - " + (i + 1);
                messageProducer.sendSyncMessage("oom-test-topic", message);
                successCount++;
                
                // 稍微延迟，避免发送过快
                if (i % 10 == 0 && i > 0) {
                    Thread.sleep(100);
                    log.info("已发送 {} / {} 条消息", i, count);
                }
                
            } catch (Exception e) {
                log.error("发送第 {} 条消息失败", i + 1, e);
                failCount++;
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "批量发送完成");
        response.put("totalCount", count);
        response.put("successCount", successCount);
        response.put("failCount", failCount);
        response.put("timestamp", System.currentTimeMillis());
        
        // 附加内存统计信息
        response.put("memoryStats", memoryLeakService.getMemoryStats());
        
        return response;
    }

    /**
     * 获取OOM测试说明
     * 
     * @return 使用说明
     */
    @GetMapping("/help")
    public Map<String, Object> getHelp() {
        Map<String, Object> response = new HashMap<>();
        response.put("title", "OutOfMemoryError 测试指南");
        response.put("description", "用于演示缓慢出现的 Java heap space OOM 异常");
        
        Map<String, String> steps = new HashMap<>();
        steps.put("step1", "配置JVM参数：-Xmx256m -Xms128m（限制堆内存大小）");
        steps.put("step2", "启动内存泄漏模式：POST /oom/start?sizeKB=100");
        steps.put("step3", "批量发送消息：POST /oom/send-batch?count=100");
        steps.put("step4", "查看内存状态：GET /oom/stats");
        steps.put("step5", "重复步骤3-4，观察内存缓慢增长");
        steps.put("step6", "等待 OutOfMemoryError 发生");
        steps.put("step7", "清理内存（可选）：POST /oom/clear");
        
        response.put("steps", steps);
        
        Map<String, String> apis = new HashMap<>();
        apis.put("POST /oom/start", "启动内存泄漏模式");
        apis.put("POST /oom/stop", "停止内存泄漏模式");
        apis.put("POST /oom/clear", "清理累积的内存");
        apis.put("GET /oom/stats", "查看内存统计");
        apis.put("POST /oom/send", "发送单条测试消息");
        apis.put("POST /oom/send-batch", "批量发送测试消息");
        
        response.put("apis", apis);
        response.put("warning", "⚠️⚠️⚠️ 此功能会导致真实的内存溢出，仅在测试环境使用！");
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }
}

