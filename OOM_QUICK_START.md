# OutOfMemoryError 快速入门指南

## 🚀 5分钟快速体验

### 方法1：使用自动化脚本（最简单）

#### Windows:
```bash
# 1. 启动应用（限制内存）
start-oom-test.bat

# 2. 等待应用启动后，打开新终端运行测试脚本
quick-oom-test.bat
```

#### Linux/Mac:
```bash
# 1. 给脚本添加执行权限
chmod +x start-oom-test.sh quick-oom-test.sh

# 2. 启动应用（限制内存）
./start-oom-test.sh

# 3. 等待应用启动后，打开新终端运行测试脚本
./quick-oom-test.sh
```

### 方法2：手动测试（更灵活）

#### 步骤1：启动应用（限制堆内存）
```bash
# 使用 Maven（推荐）
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx256m -Xms128m"

# 或者在 IDEA 中配置 VM options:
# -Xmx256m -Xms128m -XX:+HeapDumpOnOutOfMemoryError
```

#### 步骤2：启动内存泄漏模式
```bash
curl -X POST "http://localhost:8080/oom/start?sizeKB=100"
```

#### 步骤3：发送测试消息
```bash
# 发送100条消息
curl -X POST "http://localhost:8080/oom/send-batch?count=100"
```

#### 步骤4：查看内存状态
```bash
curl -X GET "http://localhost:8080/oom/stats"
```

#### 步骤5：重复步骤3-4，直到 OOM

---

## 📊 预期结果

### 第1轮（内存使用率约30%）
```json
{
  "usedMemoryMB": "75.50 MB",
  "maxMemoryMB": "256.00 MB",
  "usedPercentage": "29.49%",
  "messageCount": 100
}
```

### 第5轮（内存使用率约70%）
```json
{
  "usedMemoryMB": "180.50 MB",
  "maxMemoryMB": "256.00 MB",
  "usedPercentage": "70.51%",
  "messageCount": 500
}
```

### 第8轮（内存警告）
```
⚠️⚠️⚠️ 警告：内存使用率已超过80%！即将发生 OOM！
```

### 第12轮（OOM 发生）
```
💥💥💥 OutOfMemoryError 发生了！消息数: 1250
Exception in thread "ConsumeMessageThread_oom-consumer-group_1" 
java.lang.OutOfMemoryError: Java heap space
```

---

## 🔍 监控工具

### 1. 实时日志监控
观察控制台输出，每处理10条消息会打印一次内存统计：
```
📊 内存统计 - 消息数: 100, 已用: 180.50 MB / 256.00 MB, 使用率: 70.51%, 缓存大小: 100
```

### 2. JConsole（推荐）
```bash
# 启动 JConsole
jconsole

# 选择 MqLimitDemoApplication 进程
# 切换到 Memory 标签页
# 观察 Heap Memory Usage 图表
```

### 3. VisualVM（更强大）
```bash
# 启动 VisualVM
jvisualvm

# 双击应用进程
# 切换到 Monitor 标签页
# 观察堆内存使用曲线
```

### 4. 堆转储文件分析
OOM 发生后会生成 `heap_dump.hprof` 文件：
```bash
# 使用 jhat 分析（JDK自带）
jhat heap_dump.hprof
# 浏览器访问 http://localhost:7000

# 或使用 Eclipse MAT（推荐）
# 下载：https://www.eclipse.org/mat/
```

---

## ⚙️ 调整测试速度

### 快速模式（1-2分钟触发OOM）
```bash
# 更小的堆内存
-Xmx128m -Xms64m

# 更大的对象
curl -X POST "http://localhost:8080/oom/start?sizeKB=500"

# 更多消息
curl -X POST "http://localhost:8080/oom/send-batch?count=200"
```

### 缓慢模式（10-15分钟触发OOM）
```bash
# 更大的堆内存
-Xmx512m -Xms256m

# 更小的对象
curl -X POST "http://localhost:8080/oom/start?sizeKB=50"

# 较少消息
curl -X POST "http://localhost:8080/oom/send-batch?count=50"
```

---

## 🛑 停止和清理

### 停止内存泄漏（不清理已累积内存）
```bash
curl -X POST "http://localhost:8080/oom/stop"
```

### 清理所有累积的内存
```bash
curl -X POST "http://localhost:8080/oom/clear"
```

### 重启应用（最彻底）
```bash
# Ctrl+C 停止应用，然后重新启动
mvn spring-boot:run
```

---

## 🎯 学习目标

通过这个 OOM 模拟场景，你可以学习到：

1. **内存泄漏的本质**
   - 对象无法被 GC 回收
   - 静态集合的风险
   - 内存缓慢累积的过程

2. **OOM 的形成过程**
   - 内存使用率逐渐增长
   - GC 频繁但无效
   - 最终抛出 OutOfMemoryError

3. **监控和排查方法**
   - 实时内存监控
   - 堆转储文件分析
   - 定位内存泄漏源

4. **预防措施**
   - 合理配置 JVM 参数
   - 避免静态集合滥用
   - 及时释放不用的对象
   - 使用弱引用/软引用

---

## ❓ 常见问题

### Q: 为什么发送了很多消息但没有 OOM？
**A:** 可能的原因：
- 堆内存设置过大，建议 `-Xmx256m`
- GC 回收速度快，增加 `sizeKB` 参数
- 未启动内存泄漏模式，先调用 `/oom/start`

### Q: OOM 后应用无法恢复怎么办？
**A:** OutOfMemoryError 是严重错误，需要重启应用：
```bash
# 停止应用（Ctrl+C）
# 清理内存并重启
curl -X POST "http://localhost:8080/oom/clear"
mvn spring-boot:run
```

### Q: 如何在 IDEA 中配置 JVM 参数？
**A:** 
1. Run -> Edit Configurations
2. 选择 MqLimitDemoApplication
3. VM options: `-Xmx256m -Xms128m -XX:+HeapDumpOnOutOfMemoryError`

---

## 📚 详细文档

完整的技术细节和高级用法请查看：
- **[OOM_TEST_GUIDE.md](OOM_TEST_GUIDE.md)** - 完整测试指南
- **[README.md](README.md)** - 项目总览

---

## ⚠️ 重要提醒

**此功能会导致真实的内存溢出，仅在测试环境使用！**

切勿在生产环境运行此测试，否则可能导致：
- 应用崩溃
- 服务不可用
- 数据丢失（如果没有持久化）

---

**祝你测试愉快！🎉**

