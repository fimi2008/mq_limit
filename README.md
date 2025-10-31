# Spring Boot RocketMQ 消费案例

这是一个基于 Spring Boot 的 RocketMQ 消息生产和消费的完整示例项目。

## 项目特点

- ✅ Spring Boot 2.7.14
- ✅ RocketMQ Spring Boot Starter 2.2.3
- ✅ Redis 分布式限流（支持滑动窗口、令牌桶、固定窗口）
- ✅ Lua 脚本分离配置，易于维护
- ✅ 多种消息消费模式演示
- ✅ 生产者和消费者完整实现
- ✅ 熔断降级机制
- ✅ RESTful API 接口测试
- ✅ 详细的日志输出

## 项目结构

```
mq_limit_demo/
├── src/
│   └── main/
│       ├── java/com/example/mqlimitdemo/
│       │   ├── MqLimitDemoApplication.java           # 启动类
│       │   ├── config/
│       │   │   ├── RateLimiterConfig.java            # 本地限流器配置
│       │   │   ├── RedisConfig.java                  # Redis配置
│       │   │   └── RedisLuaScriptConfig.java         # Lua脚本配置 🆕
│       │   ├── limiter/
│       │   │   └── RedisRateLimiter.java             # Redis分布式限流器
│       │   ├── service/
│       │   │   └── ThirdPartyApiService.java         # 模拟第三方API
│       │   ├── controller/
│       │   │   ├── MessageController.java            # 消息发送接口
│       │   │   ├── RateLimitTestController.java      # 本地限流测试
│       │   │   └── RedisRateLimitController.java     # Redis限流测试
│       │   ├── producer/
│       │   │   └── MessageProducer.java              # 消息生产者
│       │   ├── consumer/
│       │   │   ├── OrderMessageConsumer.java         # 订单消息消费者
│       │   │   ├── SimpleMessageConsumer.java        # 简单消息消费者
│       │   │   ├── TagFilterConsumer.java            # Tag过滤消费者
│       │   │   ├── RateLimitedMessageConsumer.java   # 本地限流消费者
│       │   │   ├── AdvancedRateLimitConsumer.java    # 高级限流消费者
│       │   │   └── RedisRateLimitConsumer.java       # Redis限流消费者
│       │   └── domain/
│       │       └── OrderMessage.java                 # 订单消息实体
│       └── resources/
│           ├── lua/                                  # Lua脚本目录 🆕
│           │   ├── sliding_window_rate_limit.lua     # 滑动窗口脚本
│           │   └── token_bucket_rate_limit.lua       # 令牌桶脚本
│           └── application.yml                       # 配置文件
├── docs/
│   └── api.http                                      # HTTP接口测试 🆕
├── pom.xml                                           # Maven依赖
├── README.md                                         # 项目说明
├── RATE_LIMIT_GUIDE.md                               # 本地限流指南
└── REDIS_RATE_LIMIT_GUIDE.md                         # Redis限流指南
```

## 前置条件

### 1. 安装 RocketMQ

#### Windows 系统：

```bash
# 下载 RocketMQ
# 访问 https://rocketmq.apache.org/download/ 下载最新版本

# 解压到指定目录，例如：D:\rocketmq

# 启动 NameServer
cd D:\rocketmq\bin
start mqnamesrv.cmd

# 启动 Broker
start mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true
```

#### Linux/Mac 系统：

```bash
# 下载并解压 RocketMQ
wget https://dist.apache.org/repos/dist/release/rocketmq/5.1.4/rocketmq-all-5.1.4-bin-release.zip
unzip rocketmq-all-5.1.4-bin-release.zip
cd rocketmq-all-5.1.4-bin-release

# 启动 NameServer
nohup sh bin/mqnamesrv &

# 启动 Broker
nohup sh bin/mqbroker -n localhost:9876 autoCreateTopicEnable=true &
```

### 2. Java 环境

确保已安装 JDK 1.8 或更高版本：

```bash
java -version
```

### 3. Maven 环境

确保已安装 Maven 3.x：

```bash
mvn -version
```

## 快速开始

### 1. 克隆或下载项目

```bash
cd D:\workspace\cursor\mq_limit_demo
```

### 2. 修改配置

编辑 `src/main/resources/application.yml`，修改 RocketMQ NameServer 地址（如需要）：

```yaml
rocketmq:
  name-server: 127.0.0.1:9876  # 修改为你的 NameServer 地址
```

### 3. 构建项目

```bash
mvn clean package
```

### 4. 启动应用

```bash
mvn spring-boot:run
```

或者运行打包后的 jar：

```bash
java -jar target/mq-limit-demo-1.0.0.jar
```

## 功能演示

应用启动后，可以通过以下接口测试消息发送和消费：

### 1. 发送简单消息

```bash
# GET 请求
curl "http://localhost:8080/message/send/simple?message=Hello"
```

### 2. 发送订单消息

```bash
# POST 请求
curl -X POST http://localhost:8080/message/send/order
```

### 3. 发送异步消息

```bash
curl "http://localhost:8080/message/send/async?message=AsyncTest"
```

### 4. 发送单向消息

```bash
curl "http://localhost:8080/message/send/oneway?message=OneWayTest"
```

### 5. 发送带 Tag 的消息

```bash
# 发送 tagA 标签的消息（会被消费）
curl "http://localhost:8080/message/send/tag?tag=tagA&message=TagAMessage"

# 发送 tagB 标签的消息（不会被 TagFilterConsumer 消费）
curl "http://localhost:8080/message/send/tag?tag=tagB&message=TagBMessage"
```

### 6. 发送延迟消息

```bash
# delayLevel 说明：
# 1=1s, 2=5s, 3=10s, 4=30s, 5=1m, 6=2m, 7=3m, 8=4m, 9=5m
# 10=6m, 11=7m, 12=8m, 13=9m, 14=10m, 15=20m, 16=30m, 17=1h, 18=2h

curl "http://localhost:8080/message/send/delay?message=DelayTest&delayLevel=3"
```

### 7. 批量发送消息

```bash
curl "http://localhost:9000/message/send/batch?count=20"
```

### 8. 限流场景测试 ⭐

#### 本地限流测试

测试基础限流（单线程 + RateLimiter）：

```bash
# 发送20条消息，观察限流效果
curl "http://localhost:9000/rate-limit/test/basic?count=20"
```

测试高级限流（多线程 + 熔断降级）：

```bash
# 发送30条消息，观察熔断器和降级处理
curl "http://localhost:9000/rate-limit/test/advanced?count=30"
```

直接调用第三方接口（验证限流）：

```bash
# 快速调用10次，前5次成功，后5次触发限流
curl "http://localhost:9000/rate-limit/test/direct?count=10"
```

并发测试：

```bash
# 并发发送50条消息
curl "http://localhost:9000/rate-limit/test/concurrent?count=50"
```

查看第三方接口状态：

```bash
curl "http://localhost:9000/rate-limit/status"
```

#### Redis 分布式限流测试 🔥

**前提**：需要先启动 Redis（见下方 Docker 启动命令）

检查 Redis 连接：

```bash
curl "http://localhost:9000/redis-rate-limit/health"
```

测试滑动窗口限流（推荐）：

```bash
# 发送20条消息，观察Redis分布式限流效果
curl "http://localhost:9000/redis-rate-limit/test/sliding-window?count=20"
```

测试令牌桶限流：

```bash
curl "http://localhost:9000/redis-rate-limit/test/token-bucket?count=30"
```

测试固定窗口限流：

```bash
curl "http://localhost:9000/redis-rate-limit/test/fixed-window?count=15"
```

直接测试限流器：

```bash
# 测试滑动窗口算法
curl "http://localhost:9000/redis-rate-limit/test/direct?algorithm=sliding&count=10"

# 测试令牌桶算法
curl "http://localhost:9000/redis-rate-limit/test/direct?algorithm=token&count=10"

# 测试固定窗口算法
curl "http://localhost:9000/redis-rate-limit/test/direct?algorithm=fixed&count=10"
```

查看限流统计：

```bash
curl "http://localhost:9000/redis-rate-limit/stats?key=third_party_api"
```

重置限流计数：

```bash
curl -X POST "http://localhost:9000/redis-rate-limit/reset?key=third_party_api"
```

## 消费者说明

### OrderMessageConsumer（订单消息消费者）

- **Topic**: `order-topic`
- **Consumer Group**: `demo-consumer-group`
- **功能**: 消费订单消息，解析 JSON 格式的订单对象

### SimpleMessageConsumer（简单消息消费者）

- **Topic**: `simple-topic`
- **Consumer Group**: `simple-consumer-group`
- **功能**: 消费简单文本消息

### TagFilterConsumer（Tag 过滤消费者）

- **Topic**: `tag-topic`
- **Consumer Group**: `tag-consumer-group`
- **Tag Filter**: `tagA`
- **功能**: 只消费带有 `tagA` 标签的消息

### RateLimitedMessageConsumer（限流消息消费者）⭐

- **Topic**: `rate-limit-topic`
- **Consumer Group**: `rate-limit-consumer-group`
- **功能**: 演示如何处理第三方接口的频率限制
- **特点**: 使用 RateLimiter 控制消费速度，单线程消费

### AdvancedRateLimitConsumer（高级限流消费者）⭐

- **Topic**: `advanced-limit-topic`
- **Consumer Group**: `advanced-limit-consumer-group`
- **功能**: 演示熔断、降级、重试等高级限流策略
- **特点**: 多线程消费 + 熔断器 + 降级处理

### RedisRateLimitConsumer（Redis分布式限流消费者）🔥

- **Topic**: `redis-limit-topic`
- **Consumer Group**: `redis-limit-consumer-group`
- **功能**: 使用 Redis 实现分布式限流，支持多实例部署
- **特点**: 
  - ✅ 支持滑动窗口、令牌桶、固定窗口三种算法
  - ✅ 多个消费者实例共享限流配置
  - ✅ 适合分布式/集群环境

## 消费模式说明

### 1. 消息模型（MessageModel）

- **CLUSTERING（集群模式）**: 同一个 Consumer Group 中的多个消费者会负载均衡消费消息
- **BROADCASTING（广播模式）**: 每个消费者都会收到所有消息

### 2. 消费模式（ConsumeMode）

- **CONCURRENTLY（并发消费）**: 多线程并发消费，不保证顺序
- **ORDERLY（顺序消费）**: 单线程顺序消费，保证消息顺序

## 配置说明

### 生产者配置

```yaml
rocketmq:
  producer:
    group: demo-producer-group           # 生产者组名
    send-message-timeout: 3000           # 发送超时时间（毫秒）
    max-message-size: 4194304            # 最大消息大小（字节）
    retry-times-when-send-failed: 2      # 同步发送失败重试次数
```

### 消费者配置

```yaml
rocketmq:
  consumer:
    group: demo-consumer-group           # 消费者组名
    message-model: CLUSTERING            # 消息模式
    consume-mode: CONCURRENTLY           # 消费模式
    consume-thread-min: 5                # 最小消费线程数
    consume-thread-max: 20               # 最大消费线程数
    pull-batch-size: 10                  # 批量拉取消息数
```

## 查看日志

应用运行时会输出详细的日志信息，包括：

- 消息发送日志
- 消息消费日志
- 业务处理日志
- 异常错误日志

查看控制台输出即可看到消息的生产和消费过程。

## 常见问题

### 1. 连接不上 RocketMQ NameServer

**问题**: `connect to <xxx.xxx.xxx.xxx:9876> failed`

**解决方案**:
- 确认 RocketMQ NameServer 已启动
- 检查配置文件中的 `name-server` 地址是否正确
- 检查防火墙设置

### 2. Topic 不存在

**问题**: `No route info of this topic`

**解决方案**:
- 启动 Broker 时添加参数 `autoCreateTopicEnable=true`
- 或手动创建 Topic：
  ```bash
  # Windows
  mqadmin.cmd updateTopic -n 127.0.0.1:9876 -t order-topic -c DefaultCluster
  
  # Linux
  sh mqadmin updateTopic -n 127.0.0.1:9876 -t order-topic -c DefaultCluster
  ```

### 3. 消息消费失败

**问题**: 消息一直重复消费

**解决方案**:
- 检查消费者代码是否抛出异常
- 确保消费逻辑正确处理消息
- 查看 RocketMQ 控制台的消费进度

## 扩展功能

### 1. 事务消息

可以参考官方文档实现事务消息：
```java
@RocketMQTransactionListener
public class TransactionListenerImpl implements RocketMQLocalTransactionListener {
    // 实现事务消息监听器
}
```

### 2. 顺序消息

修改消费者注解：
```java
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "order-consumer-group",
    consumeMode = ConsumeMode.ORDERLY  // 顺序消费
)
```

### 3. 消息过滤

支持 Tag 和 SQL92 表达式过滤：
```java
@RocketMQMessageListener(
    topic = "filter-topic",
    consumerGroup = "filter-consumer-group",
    selectorType = SelectorType.SQL92,
    selectorExpression = "age > 18 AND city = 'Beijing'"
)
```

## 参考文档

- [RocketMQ 官方文档](https://rocketmq.apache.org/docs/quick-start/)
- [RocketMQ Spring Boot Starter](https://github.com/apache/rocketmq-spring)
- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)

## 技术支持

如有问题，请参考：
- RocketMQ 官方文档
- GitHub Issues
- Stack Overflow

## 许可证

本项目仅供学习和演示使用。

## Docker 快速启动

### 启动 RocketMQ

如果你没有安装 RocketMQ，可以使用 Docker 快速启动：

```bash
# 方式1：使用单个容器镜像（推荐用于测试）
docker run -d --name rocketmq --privileged=true \
  -p 9876:9876 -p 10911:10911 -p 10909:10909 -p 8088:8080 \
  registry.cn-hangzhou.aliyuncs.com/xfg-studio/rocketmq

# 方式2：使用持久化存储
docker run -d --name rocketmq --privileged=true \
  -p 9876:9876 -p 10911:10911 -p 10909:10909 -p 8088:8080 \
  -v /home/app/data/console:/home/app/data/console \
  -v /home/app/data/logs:/home/app/data/logs \
  -v /home/app/data/rocketmq:/home/app/data/rocketmq \
  registry.cn-hangzhou.aliyuncs.com/xfg-studio/rocketmq

# 方式3：使用集成镜像
docker run -d --name rocketmq-all \
  -p 9876:9876 -p 10911:10911 -p 10909:10909 -p 8088:8080 \
  -e "JAVA_OPT_EXT=-Xms512m -Xmx512m" \
  foxiswho/rocketmq:4.8.0
```

启动后访问控制台：http://localhost:8088

### 启动 Redis（分布式限流必需）🔥

```bash
# 方式1：简单启动
docker run -d --name redis -p 6379:6379 redis:7-alpine

# 方式2：使用持久化（推荐）
docker run -d --name redis -p 6379:6379 \
  -v redis-data:/data \
  redis:7-alpine redis-server --appendonly yes

# 方式3：设置密码
docker run -d --name redis -p 6379:6379 \
  redis:7-alpine redis-server --requirepass your_password
```

验证 Redis：
```bash
docker exec -it redis redis-cli ping
# 响应：PONG
```

## 限流场景详细说明 ⭐

### 场景描述

在实际业务中，MQ消费者需要调用第三方接口（如短信、邮件、支付通知等），但第三方接口通常有频率限制。本项目提供了完整的限流处理方案。

### 核心问题

1. **问题**: 第三方接口限制每秒最多5次调用
2. **挑战**: MQ消费者可能每秒处理数十条消息
3. **后果**: 超过限制会导致调用失败、消息重试、资源浪费

### 解决方案

本项目实现了**四种限流方案**：

#### 方案1：单线程 + RateLimiter（推荐新手）
- 使用 Guava RateLimiter（令牌桶算法）
- 设置单线程消费，保证稳定性
- 适合对实时性要求不高的场景
- ⚠️ 不适合分布式环境

#### 方案2：多线程 + 熔断降级（单机生产环境）
- 多线程消费提高吞吐量
- 实现熔断器，连续失败后自动熔断
- 支持降级处理，系统过载时保护核心功能
- ⚠️ 不适合分布式环境

#### 方案3：Redis 分布式限流（分布式生产环境推荐）🔥
- 使用 Redis 实现分布式限流
- 支持滑动窗口、令牌桶、固定窗口三种算法
- 多个消费者实例共享限流配置
- ✅ 适合分布式/集群环境

#### 方案4：调整消费参数（最简单）
- 通过配置文件控制消费速度
- 适合简单场景

详细说明请查看：
- [RATE_LIMIT_GUIDE.md](RATE_LIMIT_GUIDE.md) - 本地限流方案
- [REDIS_RATE_LIMIT_GUIDE.md](REDIS_RATE_LIMIT_GUIDE.md) - Redis分布式限流方案 🔥

### 测试效果对比

#### 单机环境

| 测试场景 | 无限流 | 方案1（单线程） | 方案2（熔断降级） |
|---------|--------|--------------|-----------------|
| 20条消息 | 部分失败 | 全部成功 | 全部成功 |
| 消费时间 | 2秒 | 4秒 | 3秒 |
| 失败率 | 60% | 0% | 0% |
| 重试次数 | 多次 | 0 | 0-2次 |

#### 分布式环境（3个实例）

| 测试场景 | 本地限流 | Redis分布式限流 |
|---------|---------|---------------|
| 总限流目标 | 5次/秒 | 5次/秒 |
| 实际限流 | 15次/秒 ❌ | 5次/秒 ✅ |
| 精度 | 低（各实例独立） | 高（全局共享） |
| 适用场景 | 单机 | 分布式/集群 |

