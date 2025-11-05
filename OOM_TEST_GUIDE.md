# OutOfMemoryError 测试指南

## 📝 概述

本项目提供了一个完整的 `OutOfMemoryError: Java heap space` 异常模拟场景，用于演示和学习：
- 内存泄漏的形成过程
- 内存缓慢增长的监控
- OOM 异常的触发机制
- 生产环境问题排查思路

**⚠️ 警告：此功能会导致真实的内存溢出，仅在测试环境使用！**

---

## 🎯 设计原理

### 1. 内存泄漏机制

```java
// 使用静态集合存储数据，防止 GC 回收
private static final Map<String, List<byte[]>> MEMORY_LEAK_CACHE = new ConcurrentHashMap<>();

// 每次处理消息时添加大对象
byte[] data = new byte[100 * 1024]; // 100KB
MEMORY_LEAK_CACHE.put(key, Arrays.asList(data));
```

### 2. 缓慢累积

- 每条消息消费时累积约 100KB 数据（可配置）
- 数据存储在静态集合中，不会被 GC 回收
- 随着消息不断消费，内存持续增长
- 最终触发 `OutOfMemoryError`

### 3. 实时监控

- 每处理 10 条消息打印一次内存统计
- 内存使用率超过 80% 时发出警告
- 提供 REST API 查看实时内存状态

---

## 🚀 快速开始

### 步骤 1：配置 JVM 参数

为了快速看到效果，需要限制 JVM 堆内存大小。

#### IDEA 配置方法：
1. 打开 `Run` -> `Edit Configurations`
2. 选择 `MqLimitDemoApplication`
3. 在 `VM options` 中添加：

```bash
-Xmx256m -Xms128m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heap_dump.hprof
```

#### 参数说明：
- `-Xmx256m`：最大堆内存 256MB
- `-Xms128m`：初始堆内存 128MB
- `-XX:+HeapDumpOnOutOfMemoryError`：OOM 时自动生成堆转储文件
- `-XX:HeapDumpPath`：堆转储文件保存路径

### 步骤 2：启动应用

```bash
# 方式1：IDEA 中直接运行
Run MqLimitDemoApplication

# 方式2：Maven 命令启动
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx256m -Xms128m"

# 方式3：打包后运行
mvn clean package
java -Xmx256m -Xms128m -jar target/mq-limit-demo-0.0.1-SNAPSHOT.jar
```

### 步骤 3：启动内存泄漏模式

```bash
# 启动内存泄漏模式（每条消息累积 100KB）
curl -X POST "http://localhost:8080/oom/start?sizeKB=100"

# 返回示例：
{
  "success": true,
  "message": "内存泄漏模式已启动",
  "objectSizeKB": 100,
  "warning": "⚠️ 这将导致真实的内存溢出！",
  "timestamp": 1699188000000
}
```

### 步骤 4：发送测试消息

```bash
# 批量发送 100 条消息
curl -X POST "http://localhost:8080/oom/send-batch?count=100"

# 返回示例：
{
  "success": true,
  "message": "批量发送完成",
  "totalCount": 100,
  "successCount": 100,
  "failCount": 0,
  "memoryStats": {
    "usedMemoryMB": "180.50 MB",
    "maxMemoryMB": "256.00 MB",
    "usedPercentage": "70.51%"
  }
}
```

### 步骤 5：监控内存状态

```bash
# 查看实时内存统计
curl -X GET "http://localhost:8080/oom/stats"

# 返回示例：
{
  "success": true,
  "leakEnabled": true,
  "messageCount": 100,
  "cacheSize": 100,
  "objectSizeKB": 100,
  "runningTimeSeconds": 45,
  "maxMemoryMB": "256.00 MB",
  "totalMemoryMB": "256.00 MB",
  "usedMemoryMB": "180.50 MB",
  "freeMemoryMB": "75.50 MB",
  "usedPercentage": "70.51%",
  "estimatedLeakSizeMB": 9
}
```

### 步骤 6：持续发送直到 OOM

```bash
# 多次批量发送消息，观察内存增长
for i in {1..10}; do
  echo "第 $i 轮发送..."
  curl -X POST "http://localhost:8080/oom/send-batch?count=50"
  sleep 2
  curl -X GET "http://localhost:8080/oom/stats" | jq '.usedPercentage'
done
```

### 步骤 7：观察 OOM 发生

当内存使用率达到 100% 时，会抛出 `OutOfMemoryError`：

```
⚠️⚠️⚠️ 警告：内存使用率已超过80%！即将发生 OOM！
📊 内存统计 - 消息数: 1200, 已用: 245.30 MB / 256.00 MB, 使用率: 95.82%

💥💥💥 OutOfMemoryError 发生了！消息数: 1250
💥 错误详情: Java heap space
Exception in thread "ConsumeMessageThread_oom-consumer-group_1" java.lang.OutOfMemoryError: Java heap space
```

---

## 📊 API 接口说明

### 1. 启动内存泄漏模式

**接口：** `POST /oom/start`

**参数：**
- `sizeKB`（可选）：每条消息累积的对象大小（KB），默认 100

**示例：**
```bash
curl -X POST "http://localhost:8080/oom/start?sizeKB=100"
```

### 2. 停止内存泄漏模式

**接口：** `POST /oom/stop`

**说明：** 停止新的内存累积，但已累积的内存不会被清理

**示例：**
```bash
curl -X POST "http://localhost:8080/oom/stop"
```

### 3. 清理累积的内存

**接口：** `POST /oom/clear`

**说明：** 清空所有累积的内存数据，触发 GC 回收

**示例：**
```bash
curl -X POST "http://localhost:8080/oom/clear"
```

### 4. 查看内存统计

**接口：** `GET /oom/stats`

**返回字段说明：**
- `leakEnabled`：内存泄漏模式是否启用
- `messageCount`：已处理的消息数量
- `cacheSize`：缓存中的对象数量
- `objectSizeKB`：每个对象的大小
- `runningTimeSeconds`：运行时长（秒）
- `maxMemoryMB`：最大堆内存
- `usedMemoryMB`：已使用堆内存
- `usedPercentage`：内存使用率
- `estimatedLeakSizeMB`：估算的泄漏内存大小

**示例：**
```bash
curl -X GET "http://localhost:8080/oom/stats"
```

### 5. 发送单条测试消息

**接口：** `POST /oom/send`

**参数：**
- `message`（可选）：消息内容

**示例：**
```bash
curl -X POST "http://localhost:8080/oom/send?message=Test"
```

### 6. 批量发送测试消息

**接口：** `POST /oom/send-batch`

**参数：**
- `count`（可选）：发送数量，默认 50

**示例：**
```bash
curl -X POST "http://localhost:8080/oom/send-batch?count=100"
```

### 7. 获取使用帮助

**接口：** `GET /oom/help`

**示例：**
```bash
curl -X GET "http://localhost:8080/oom/help"
```

---

## 🔍 监控和排查

### 1. 日志监控

应用日志会实时输出内存统计信息：

```
INFO  - 📊 内存统计 - 消息数: 100, 已用: 180.50 MB / 256.00 MB, 使用率: 70.51%, 缓存大小: 100
WARN  - ⚠️⚠️⚠️ 警告：内存使用率已超过80%！即将发生 OOM！
INFO  - 📊 内存统计 - 消息数: 1100, 已用: 225.30 MB / 256.00 MB, 使用率: 88.01%, 缓存大小: 1100
ERROR - 💥💥💥 OutOfMemoryError 发生了！消息数: 1250
```

### 2. JConsole 监控

1. 启动应用后，运行 `jconsole`
2. 连接到 `MqLimitDemoApplication` 进程
3. 切换到 `Memory` 标签页
4. 观察 `Heap Memory Usage` 图表
5. 可以看到内存持续增长，直到达到最大值

### 3. VisualVM 监控

1. 启动 VisualVM：`jvisualvm`
2. 双击应用进程
3. 切换到 `Monitor` 标签页
4. 观察堆内存使用情况
5. 可以在 `Sampler` 中查看对象分布

### 4. 堆转储分析

OOM 发生后会自动生成 `heap_dump.hprof` 文件，可以使用以下工具分析：

#### 使用 Eclipse MAT (Memory Analyzer Tool)：
1. 下载 MAT：https://www.eclipse.org/mat/
2. 打开 `heap_dump.hprof` 文件
3. 查看 `Leak Suspects Report`
4. 分析占用内存最多的对象

#### 使用 VisualVM：
```bash
jvisualvm --openfile heap_dump.hprof
```

#### 使用 jhat（JDK 自带）：
```bash
jhat heap_dump.hprof
# 浏览器访问 http://localhost:7000
```

---

## 📈 不同场景下的配置

### 场景 1：快速演示（1-2分钟触发 OOM）

```bash
# JVM 参数
-Xmx128m -Xms64m

# 启动配置
curl -X POST "http://localhost:8080/oom/start?sizeKB=500"

# 发送消息
curl -X POST "http://localhost:8080/oom/send-batch?count=200"
```

**预期：** 约 1-2 分钟后触发 OOM

### 场景 2：缓慢演示（5-10分钟触发 OOM）

```bash
# JVM 参数
-Xmx256m -Xms128m

# 启动配置
curl -X POST "http://localhost:8080/oom/start?sizeKB=100"

# 持续发送消息
for i in {1..20}; do
  curl -X POST "http://localhost:8080/oom/send-batch?count=50"
  sleep 10
done
```

**预期：** 约 5-10 分钟后触发 OOM

### 场景 3：生产环境模拟（30分钟以上）

```bash
# JVM 参数
-Xmx512m -Xms256m

# 启动配置
curl -X POST "http://localhost:8080/oom/start?sizeKB=50"

# 定时发送消息（模拟真实流量）
while true; do
  curl -X POST "http://localhost:8080/oom/send-batch?count=10"
  sleep 30
done
```

**预期：** 30 分钟以上后触发 OOM

---

## 🛠️ 常见问题

### Q1：为什么发送了很多消息但内存没有明显增长？

**A：** 可能的原因：
1. 未启动内存泄漏模式，先调用 `/oom/start`
2. JVM 堆内存设置过大，建议设置为 `-Xmx256m`
3. GC 回收速度较快，可以增加 `sizeKB` 参数

### Q2：如何加速 OOM 的触发？

**A：** 可以采取以下措施：
1. 减小 JVM 堆内存：`-Xmx128m`
2. 增加对象大小：`sizeKB=500`
3. 增加发送频率：`count=200`

### Q3：OOM 后应用会自动恢复吗？

**A：** 不会。`OutOfMemoryError` 是严重错误，通常需要重启应用。可以：
1. 手动重启应用
2. 或者调用 `/oom/clear` 清理内存后重启

### Q4：如何防止生产环境发生 OOM？

**A：** 最佳实践：
1. **合理配置堆内存**：根据业务量设置合适的 `-Xmx` 和 `-Xms`
2. **监控内存使用**：使用 Prometheus + Grafana 监控
3. **配置告警**：内存使用率超过 80% 时告警
4. **定期 GC**：避免内存泄漏累积
5. **压力测试**：上线前进行充分的压力测试
6. **代码审查**：避免静态集合无限增长

### Q5：堆转储文件太大怎么办？

**A：** 可以使用以下方法：
1. 压缩文件：`gzip heap_dump.hprof`
2. 仅分析部分数据：`jhat -J-Xmx1g heap_dump.hprof`
3. 使用在线工具：https://heaphero.io/

---

## 🎓 学习要点

通过这个 OOM 模拟场景，你可以学习到：

1. **内存泄漏的本质**：
   - 对象无法被 GC 回收的原因
   - 静态集合导致的内存泄漏
   - 引用链的影响

2. **OOM 的形成过程**：
   - 内存缓慢增长
   - GC 频繁但无效（Full GC）
   - 最终抛出 OutOfMemoryError

3. **监控和排查手段**：
   - 实时监控内存使用率
   - 分析堆转储文件
   - 定位内存泄漏源

4. **预防和解决方案**：
   - 合理配置 JVM 参数
   - 避免静态集合滥用
   - 及时释放不用的对象
   - 使用弱引用/软引用

---

## 📚 相关文档

- [JVM 调优指南](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/)
- [Eclipse MAT 使用教程](https://www.eclipse.org/mat/docs/)
- [Java 内存模型详解](https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html)

---

## ⚠️ 免责声明

本功能仅用于教学和测试目的，**禁止在生产环境使用**。使用本功能导致的任何损失，开发者概不负责。

在使用前请确保：
1. 在隔离的测试环境中运行
2. 已保存所有重要数据
3. 了解 OOM 的影响和风险
4. 有能力快速恢复系统

---

## 📝 更新日志

- **2025-11-05**：初始版本发布
  - 实现基础的 OOM 模拟功能
  - 提供完整的 REST API
  - 添加详细的使用文档

---

**Happy Testing! 🚀**

