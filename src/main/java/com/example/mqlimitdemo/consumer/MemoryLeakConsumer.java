package com.example.mqlimitdemo.consumer;

import com.example.mqlimitdemo.service.MemoryLeakService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 内存泄漏演示消费者
 * 
 * 这个消费者用于演示缓慢出现的 OutOfMemoryError: Java heap space 异常
 * 
 * 工作原理：
 * 1. 消费 RocketMQ 消息
 * 2. 每条消息都会在内存中累积大对象（约100KB）
 * 3. 这些对象被存储在静态集合中，不会被 GC 回收
 * 4. 随着消息不断消费，内存会缓慢增长
 * 5. 最终导致 OutOfMemoryError
 * 
 * 使用步骤：
 * 1. 调用 /oom/start 启动内存泄漏模式
 * 2. 调用 /message/send/oom 发送测试消息
 * 3. 观察内存逐渐增长
 * 4. 等待 OOM 发生
 * 
 * 监控方式：
 * - 调用 /oom/stats 查看实时内存统计
 * - 观察日志中的内存使用情况
 * - 使用 JConsole 或 VisualVM 监控堆内存
 * 
 * ⚠️ 警告：此消费者会导致真实的内存溢出，仅在测试环境使用！
 *
 * @author demo
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "oom-test-topic",
        consumerGroup = "oom-consumer-group"
)
public class MemoryLeakConsumer implements RocketMQListener<String> {

    @Resource
    private MemoryLeakService memoryLeakService;

    @Override
    public void onMessage(String message) {
        try {
            log.info("🔥 收到OOM测试消息: {}", message);
            
            // 处理消息并泄漏内存
            memoryLeakService.processMessageWithLeak(
                    "MSG-" + System.currentTimeMillis(),
                    message
            );
            
            log.debug("✅ OOM测试消息处理完成: {}", message);
            
        } catch (OutOfMemoryError e) {
            // OutOfMemoryError 是 Error，不是 Exception
            // 这里捕获后记录日志，然后重新抛出
            log.error("💥💥💥 OutOfMemoryError 在消费者中发生！");
            log.error("💥 消息内容: {}", message);
            log.error("💥 错误信息: {}", e.getMessage(), e);
            
            // 打印最终的内存统计
            memoryLeakService.logMemoryStats();
            
            // 重新抛出，让上层感知到错误
            throw e;
            
        } catch (Exception e) {
            log.error("处理OOM测试消息失败: {}", message, e);
            throw e;
        }
    }
}

