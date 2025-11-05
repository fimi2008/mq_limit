package com.example.mqlimitdemo.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存泄漏模拟服务
 * 
 * 这个服务用于演示缓慢出现的 OutOfMemoryError: Java heap space 异常
 * 
 * 实现机制：
 * 1. 使用静态集合持续累积数据，防止被 GC 回收
 * 2. 每次处理消息时都会添加大对象到内存中
 * 3. 通过控制数据累积速度，让 OOM 缓慢出现
 * 
 * 使用场景：
 * - 模拟消息处理过程中的内存泄漏
 * - 演示内存监控和告警机制
 * - 测试系统在内存不足时的表现
 * 
 * ⚠️ 警告：此服务仅用于测试环境，会导致真实的内存溢出！
 *
 * @author demo
 */
@Slf4j
@Service
public class MemoryLeakService {

    /**
     * 静态集合存储数据，防止被 GC 回收（这是导致内存泄漏的关键）
     */
    private static final Map<String, List<byte[]>> MEMORY_LEAK_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 记录累积的消息数量
     */
    private static final AtomicLong MESSAGE_COUNT = new AtomicLong(0);
    
    /**
     * 控制是否启用内存泄漏模式
     */
    private static final AtomicBoolean LEAK_ENABLED = new AtomicBoolean(false);
    
    /**
     * 记录服务启动时间
     */
    private static long startTime = 0;
    
    /**
     * 每次添加的对象大小（KB）
     */
    private static int objectSizeKB = 100;

    /**
     * 启动内存泄漏模式
     * 
     * @param sizeKB 每个对象的大小（KB），默认100KB
     */
    public void startMemoryLeak(int sizeKB) {
        if (LEAK_ENABLED.compareAndSet(false, true)) {
            objectSizeKB = sizeKB;
            startTime = System.currentTimeMillis();
            log.warn("⚠️⚠️⚠️ 内存泄漏模式已启动！每个消息将消耗约 {} KB 内存", sizeKB);
            log.warn("⚠️⚠️⚠️ 警告：这将导致真实的内存溢出，请确保在测试环境中使用！");
        } else {
            log.warn("内存泄漏模式已经在运行中");
        }
    }

    /**
     * 停止内存泄漏模式（但不清理已累积的内存）
     */
    public void stopMemoryLeak() {
        if (LEAK_ENABLED.compareAndSet(true, false)) {
            log.info("✅ 内存泄漏模式已停止（已累积的内存未清理）");
        }
    }

    /**
     * 清理所有累积的内存数据
     */
    public void clearMemory() {
        MEMORY_LEAK_CACHE.clear();
        MESSAGE_COUNT.set(0);
        log.info("✅ 已清理所有累积的内存数据，等待 GC 回收...");
        // 建议 GC 回收（仅建议，不保证立即执行）
        System.gc();
    }

    /**
     * 检查是否启用了内存泄漏模式
     */
    public boolean isLeakEnabled() {
        return LEAK_ENABLED.get();
    }

    /**
     * 模拟消息处理，每次都会泄漏一定内存
     * 
     * @param messageId 消息ID
     * @param content   消息内容
     */
    public void processMessageWithLeak(String messageId, String content) {
        if (!LEAK_ENABLED.get()) {
            log.debug("内存泄漏模式未启用，跳过处理");
            return;
        }

        try {
            // 创建大对象来消耗内存（每个对象约 objectSizeKB KB）
            byte[] data = new byte[objectSizeKB * 1024];
            
            // 填充一些数据（避免数组压缩优化）
            for (int i = 0; i < data.length; i += 100) {
                data[i] = (byte) (i % 256);
            }

            // 将数据添加到静态集合中（这会导致内存泄漏）
            String key = "msg_" + MESSAGE_COUNT.incrementAndGet();
            List<byte[]> list = MEMORY_LEAK_CACHE.computeIfAbsent(key, k -> new ArrayList<>());
            list.add(data);

            // 添加额外的元数据（进一步增加内存消耗）
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("messageId", messageId);
            metadata.put("content", content);
            metadata.put("timestamp", System.currentTimeMillis());
            metadata.put("threadName", Thread.currentThread().getName());
            // 将元数据也转换为字节数组存储
            list.add(metadata.toString().getBytes());

            // 定期打印内存统计信息
            long count = MESSAGE_COUNT.get();
            if (count % 10 == 0) {
                logMemoryStats();
            }

        } catch (OutOfMemoryError e) {
            log.error("💥💥💥 OutOfMemoryError 发生了！消息数: {}", MESSAGE_COUNT.get());
            log.error("💥 错误详情: {}", e.getMessage(), e);
            // OOM 发生后自动停止泄漏模式
            LEAK_ENABLED.set(false);
            throw e;
        }
    }

    /**
     * 获取内存统计信息
     */
    public Map<String, Object> getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double usedPercentage = (double) usedMemory / maxMemory * 100;
        long runningTime = LEAK_ENABLED.get() ? (System.currentTimeMillis() - startTime) / 1000 : 0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("leakEnabled", LEAK_ENABLED.get());
        stats.put("messageCount", MESSAGE_COUNT.get());
        stats.put("cacheSize", MEMORY_LEAK_CACHE.size());
        stats.put("objectSizeKB", objectSizeKB);
        stats.put("runningTimeSeconds", runningTime);
        
        stats.put("maxMemoryMB", formatMemory(maxMemory));
        stats.put("totalMemoryMB", formatMemory(totalMemory));
        stats.put("usedMemoryMB", formatMemory(usedMemory));
        stats.put("freeMemoryMB", formatMemory(freeMemory));
        stats.put("usedPercentage", String.format("%.2f%%", usedPercentage));
        
        // 估算累积的内存大小
        long estimatedLeakSize = MESSAGE_COUNT.get() * objectSizeKB;
        stats.put("estimatedLeakSizeMB", estimatedLeakSize / 1024);
        
        return stats;
    }

    /**
     * 打印内存统计信息到日志
     */
    public void logMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double usedPercentage = (double) usedMemory / maxMemory * 100;

        log.info("📊 内存统计 - 消息数: {}, 已用: {} / {}, 使用率: {}%, 缓存大小: {}",
                MESSAGE_COUNT.get(),
                formatMemory(usedMemory),
                formatMemory(maxMemory),
                String.format("%.2f",usedPercentage),
                MEMORY_LEAK_CACHE.size());

        // 当内存使用率超过80%时发出警告
        if (usedPercentage > 80) {
            log.warn("⚠️⚠️⚠️ 警告：内存使用率已超过80%！即将发生 OOM！");
        }
    }

    /**
     * 格式化内存大小（转换为MB）
     */
    private String formatMemory(long bytes) {
        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(bytes / (1024.0 * 1024.0)) + " MB";
    }

    /**
     * 获取消息计数
     */
    public long getMessageCount() {
        return MESSAGE_COUNT.get();
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return MEMORY_LEAK_CACHE.size();
    }
}

