# RocketMQ 消费者限流处理方案

## 场景说明

在实际业务中，消费者在处理消息时需要调用第三方接口，但第三方接口往往有频率限制（例如：每秒最多5次调用）。如果不加控制，可能导致：

1. 触发第三方接口的限流机制，调用失败
2. 消息消费失败，进入重试队列
3. 系统资源浪费，影响整体性能

## 解决方案概览

本项目实现了三种限流处理方案：

### 方案1：单线程 + RateLimiter（推荐）

**文件**: `RateLimitedMessageConsumer.java`

**核心思路**：
- 使用 Guava RateLimiter（令牌桶算法）控制消费速度
- 设置单线程消费（`consumeThreadMax = 1`）
- 阻塞式获取令牌，保证不超过限制

**优点**：
- 实现简单，稳定可靠
- 不会触发第三方接口限流
- 适合对实时性要求不高的场景

**缺点**：
- 消费速度受限
- 单线程可能成为瓶颈

```java
@RocketMQMessageListener(
    topic = "rate-limit-topic",
    consumerGroup = "rate-limit-consumer-group",
    consumeThreadMax = 1  // 单线程消费
)
public class RateLimitedMessageConsumer implements RocketMQListener<String> {
    
    @Resource(name = "thirdPartyApiRateLimiter")
    private RateLimiter rateLimiter;
    
    @Override
    public void onMessage(String message) {
        // 阻塞等待获取令牌
        rateLimiter.acquire();
        
        // 调用第三方接口
        thirdPartyApiService.sendMessage(message);
    }
}
```

### 方案2：多线程 + 熔断降级

**文件**: `AdvancedRateLimitConsumer.java`

**核心思路**：
- 允许多线程消费（`consumeThreadMax = 3`）
- 使用 RateLimiter 尝试获取令牌（非阻塞）
- 实现熔断器机制，连续失败后触发熔断
- 降级处理：无法获取令牌时，采用降级策略

**优点**：
- 提高消费吞吐量
- 具备熔断和降级能力
- 更适合生产环境

**缺点**：
- 实现复杂度高
- 需要配合监控告警

```java
@RocketMQMessageListener(
    topic = "advanced-limit-topic",
    consumerGroup = "advanced-limit-consumer-group",
    consumeThreadMax = 3  // 多线程消费
)
public class AdvancedRateLimitConsumer implements RocketMQListener<String> {
    
    @Override
    public void onMessage(String message) {
        // 检查熔断器状态
        if (isCircuitBreakerOpen()) {
            throw new RuntimeException("熔断器打开");
        }
        
        // 尝试获取令牌（最多等待3秒）
        boolean acquired = rateLimiter.tryAcquire(3, TimeUnit.SECONDS);
        
        if (!acquired) {
            // 降级处理
            handleDegradation(message);
            return;
        }
        
        // 调用第三方接口
        thirdPartyApiService.sendMessage(message);
    }
}
```

### 方案3：调整消费参数

通过配置文件调整消费者参数，从源头控制消费速度：

```yaml
rocketmq:
  consumer:
    consume-thread-min: 1        # 最小消费线程数
    consume-thread-max: 5        # 最大消费线程数
    pull-batch-size: 1           # 每次拉取1条消息
```

## 核心组件说明

### 1. ThirdPartyApiService（模拟第三方接口）

```java
/**
 * 模拟第三方接口，限制为每秒最多5次调用
 */
@Service
public class ThirdPartyApiService {
    private static final int MAX_CALLS_PER_SECOND = 5;
    
    public boolean sendMessage(String message) throws RateLimitException {
        // 检查调用频率
        if (callCount > MAX_CALLS_PER_SECOND) {
            throw new RateLimitException("超过频率限制");
        }
        // 模拟接口调用
        return true;
    }
}
```

### 2. RateLimiter 配置

```java
@Configuration
public class RateLimiterConfig {
    
    @Bean(name = "thirdPartyApiRateLimiter")
    public RateLimiter thirdPartyApiRateLimiter() {
        // 每秒5个令牌
        return RateLimiter.create(5.0);
    }
}
```

### 3. 熔断器机制

```java
// 连续失败5次后打开熔断器
private static final int CIRCUIT_BREAKER_THRESHOLD = 5;

// 熔断器打开10秒后尝试恢复
private static final long CIRCUIT_BREAKER_RESET_TIME = 10000;

private boolean isCircuitBreakerOpen() {
    if (circuitBreakerOpenTime > 0) {
        long elapsed = System.currentTimeMillis() - circuitBreakerOpenTime;
        if (elapsed > CIRCUIT_BREAKER_RESET_TIME) {
            // 恢复
            return false;
        }
        return true;
    }
    return false;
}
```

## 测试说明

### 1. 基础限流测试

测试单线程 + RateLimiter 方案：

```bash
# 发送20条消息到限流主题
curl "http://localhost:9000/rate-limit/test/basic?count=20"
```

**预期结果**：
- 消息以每秒5条的速度被消费
- 所有消息都能成功处理
- 不会触发第三方接口限流

### 2. 高级限流测试

测试多线程 + 熔断降级方案：

```bash
# 发送30条消息到高级限流主题
curl "http://localhost:9000/rate-limit/test/advanced?count=30"
```

**预期结果**：
- 多线程并发消费
- 可能触发熔断器
- 部分消息进入降级处理

### 3. 直接调用测试

直接调用第三方接口，验证限流效果：

```bash
# 快速调用10次第三方接口
curl "http://localhost:9000/rate-limit/test/direct?count=10"
```

**预期结果**：
- 前5次调用成功
- 后5次触发限流异常

### 4. 并发测试

模拟高并发场景：

```bash
# 并发发送50条消息
curl "http://localhost:9000/rate-limit/test/concurrent?count=50"
```

### 5. 查看接口状态

```bash
curl "http://localhost:9000/rate-limit/status"
```

## 实际应用建议

### 1. 选择合适的方案

| 场景 | 推荐方案 | 说明 |
|------|---------|------|
| 调用频率要求严格 | 方案1 | 单线程 + RateLimiter，保证不超限 |
| 需要高吞吐量 | 方案2 | 多线程 + 熔断降级 |
| 简单场景 | 方案3 | 仅调整配置参数 |

### 2. RateLimiter 参数设置

```java
// 如果第三方接口限制为每秒N次，RateLimiter设置为 N * 0.8
// 留20%余量，避免边界情况
RateLimiter.create(N * 0.8);
```

### 3. 消费线程数建议

```yaml
# 单线程消费（最稳定）
consumeThreadMax: 1

# 或者根据限流速率计算
# 假设处理1条消息需要100ms，限流为5次/秒
# 理论上需要：5次/秒 * 0.1秒 = 0.5个线程
# 实际设置1-2个线程即可
consumeThreadMax: 2
```

### 4. 监控和告警

建议监控以下指标：

```java
// 1. 消费速率
log.info("当前消费速率: {} 条/秒", consumeRate);

// 2. 失败次数
log.warn("连续失败次数: {}", consecutiveFailures);

// 3. 熔断器状态
log.error("熔断器已打开");

// 4. 降级处理次数
log.warn("降级处理次数: {}", degradationCount);
```

### 5. 异常重试策略

```yaml
# RocketMQ 消费失败重试配置
rocketmq:
  consumer:
    # 最大重试次数
    max-reconsume-times: 3
    # 重试间隔（由MQ控制）
```

## 常见问题

### Q1: 为什么使用RateLimiter而不是直接控制线程数？

**A**: RateLimiter使用令牌桶算法，可以精确控制每秒的请求次数，而线程数只能粗略控制并发度。

### Q2: 如何处理消息堆积？

**A**: 
1. 增加消费者实例（水平扩展）
2. 临时提高限流速率（协商第三方）
3. 使用批处理接口（如果第三方支持）
4. 业务降级，延迟处理非关键消息

### Q3: 熔断器打开后，消息会丢失吗？

**A**: 不会。抛出异常后，RocketMQ会将消息重新入队，稍后重试。

### Q4: 如何选择合适的限流速率？

**A**: 
```
限流速率 = 第三方接口限制 * 0.8（安全系数）
```

### Q5: 多个消费者实例如何协调限流？

**A**: 
1. 使用Redis等分布式限流工具
2. 或者每个实例设置：`总限流 / 实例数`

## 进阶优化

### 1. 分布式限流

使用 Redis + Lua 实现分布式限流：

```java
@Component
public class RedisRateLimiter {
    
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    
    public boolean tryAcquire(String key, int limit, int seconds) {
        // Lua脚本实现滑动窗口限流
        String script = "...";
        return redisTemplate.execute(...);
    }
}
```

### 2. 动态调整限流速率

根据第三方接口的实时响应，动态调整限流速率：

```java
public void adjustRateLimit(double newRate) {
    rateLimiter.setRate(newRate);
    log.info("限流速率已调整为: {} 次/秒", newRate);
}
```

### 3. 优先级队列

对重要消息优先处理：

```java
// 高优先级消息使用单独的Topic
@RocketMQMessageListener(
    topic = "high-priority-topic",
    consumerGroup = "high-priority-group"
)
```

## 总结

| 方案 | 适用场景 | 复杂度 | 可靠性 |
|------|---------|--------|--------|
| 单线程+RateLimiter | 严格限流 | 低 | ⭐⭐⭐⭐⭐ |
| 多线程+熔断降级 | 生产环境 | 高 | ⭐⭐⭐⭐ |
| 调整配置参数 | 简单场景 | 低 | ⭐⭐⭐ |

**最佳实践**：
1. 生产环境使用 **方案2（多线程+熔断降级）**
2. 配合监控告警，实时掌握系统状态
3. 预留20%的限流余量
4. 设置合理的重试和超时时间
5. 考虑使用分布式限流方案

## 相关文档

- [Guava RateLimiter 官方文档](https://github.com/google/guava/wiki/RateLimiterExplained)
- [RocketMQ 消费者最佳实践](https://rocketmq.apache.org/docs/bestPractice/06consumer)
- [限流算法详解](https://en.wikipedia.org/wiki/Token_bucket)

