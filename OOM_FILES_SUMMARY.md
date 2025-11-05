# OutOfMemoryError 测试场景 - 文件清单

## 📁 核心代码文件

### 1. 服务层
**文件**: `src/main/java/com/example/mqlimitdemo/service/MemoryLeakService.java`
- **功能**: 内存泄漏模拟服务
- **核心机制**: 
  - 使用静态 `ConcurrentHashMap` 存储数据
  - 每条消息累积约100KB数据（可配置）
  - 数据不会被GC回收
  - 实时内存统计和监控

### 2. 消费者
**文件**: `src/main/java/com/example/mqlimitdemo/consumer/MemoryLeakConsumer.java`
- **功能**: OOM 测试消费者
- **Topic**: `oom-test-topic`
- **Consumer Group**: `oom-consumer-group`
- **特点**: 捕获并记录 OutOfMemoryError

### 3. 控制器
**文件**: `src/main/java/com/example/mqlimitdemo/controller/OomTestController.java`
- **功能**: REST API 控制器
- **核心接口**:
  - `POST /oom/start` - 启动内存泄漏
  - `POST /oom/stop` - 停止内存泄漏
  - `POST /oom/clear` - 清理内存
  - `GET /oom/stats` - 查看统计
  - `POST /oom/send` - 发送单条消息
  - `POST /oom/send-batch` - 批量发送消息
  - `GET /oom/help` - 获取帮助

---

## 📄 文档文件

### 1. 完整测试指南
**文件**: `OOM_TEST_GUIDE.md`
- **内容**: 
  - 详细的设计原理
  - 完整的使用步骤
  - API接口说明
  - 监控和排查方法
  - 不同场景配置
  - 常见问题解答

### 2. 快速入门指南
**文件**: `OOM_QUICK_START.md`
- **内容**: 
  - 5分钟快速体验
  - 简化的操作步骤
  - 预期结果展示
  - 监控工具介绍

### 3. 文件清单（本文档）
**文件**: `OOM_FILES_SUMMARY.md`
- **内容**: 所有相关文件的说明

---

## 🧪 测试文件

### 1. HTTP 测试文件
**文件**: `docs/oom-test.http`
- **功能**: REST Client 测试脚本
- **支持**: IDEA HTTP Client、VS Code REST Client
- **包含**: 所有 API 接口的测试用例

---

## 🚀 启动脚本

### 1. Windows 启动脚本
**文件**: `start-oom-test.bat`
- **功能**: 以限制内存模式启动应用
- **JVM参数**: `-Xmx256m -Xms128m -XX:+HeapDumpOnOutOfMemoryError`

### 2. Linux/Mac 启动脚本
**文件**: `start-oom-test.sh`
- **功能**: 同上
- **使用**: `chmod +x start-oom-test.sh && ./start-oom-test.sh`

---

## 🎯 自动化测试脚本

### 1. Windows 快速测试脚本
**文件**: `quick-oom-test.bat`
- **功能**: 自动化 OOM 测试流程
- **操作**: 
  - 启动内存泄漏模式
  - 循环发送消息
  - 实时监控内存
  - 等待 OOM 发生

### 2. Linux/Mac 快速测试脚本
**文件**: `quick-oom-test.sh`
- **功能**: 同上
- **使用**: `chmod +x quick-oom-test.sh && ./quick-oom-test.sh`

---

## 📝 配置文件

### 1. Git 忽略规则
**文件**: `.gitignore` (已更新)
- **新增**: 
  ```
  # Heap dumps (OOM test)
  *.hprof
  heap_dump.hprof
  ```

### 2. README 更新
**文件**: `README.md` (已更新)
- **新增**: OutOfMemoryError 场景模拟章节
- **内容**: 快速开始、API接口、监控工具

---

## 🗂️ 文件结构

```
mq_limit_demo/
├── src/main/java/com/example/mqlimitdemo/
│   ├── service/
│   │   └── MemoryLeakService.java           ✅ 内存泄漏服务
│   ├── consumer/
│   │   └── MemoryLeakConsumer.java          ✅ OOM测试消费者
│   └── controller/
│       └── OomTestController.java           ✅ OOM测试控制器
│
├── docs/
│   └── oom-test.http                        ✅ HTTP测试文件
│
├── start-oom-test.bat                       ✅ Windows启动脚本
├── start-oom-test.sh                        ✅ Linux/Mac启动脚本
├── quick-oom-test.bat                       ✅ Windows快速测试
├── quick-oom-test.sh                        ✅ Linux/Mac快速测试
│
├── OOM_TEST_GUIDE.md                        ✅ 完整测试指南
├── OOM_QUICK_START.md                       ✅ 快速入门指南
├── OOM_FILES_SUMMARY.md                     ✅ 文件清单（本文档）
│
├── README.md                                ✅ 项目说明（已更新）
└── .gitignore                               ✅ Git配置（已更新）
```

---

## 🎓 使用流程

### 新手推荐流程

1. **阅读文档**
   ```
   OOM_QUICK_START.md → OOM_TEST_GUIDE.md
   ```

2. **启动应用**
   ```bash
   # Windows
   start-oom-test.bat
   
   # Linux/Mac
   ./start-oom-test.sh
   ```

3. **运行测试**
   ```bash
   # Windows
   quick-oom-test.bat
   
   # Linux/Mac
   ./quick-oom-test.sh
   ```

4. **观察结果**
   - 查看控制台日志
   - 使用 JConsole 监控
   - 分析堆转储文件

### 进阶用户流程

1. **手动控制测试**
   - 使用 `docs/oom-test.http` 测试文件
   - 或使用 curl 命令精确控制

2. **调整参数**
   - 修改 JVM 堆内存大小
   - 调整对象大小 (sizeKB)
   - 控制消息发送频率

3. **深入分析**
   - 使用 VisualVM 分析内存
   - 使用 Eclipse MAT 分析堆转储
   - 追踪内存泄漏源头

---

## 📊 技术要点

### 内存泄漏机制
```java
// 静态集合持有引用，防止GC回收
private static final Map<String, List<byte[]>> MEMORY_LEAK_CACHE;

// 每次处理消息时添加大对象
byte[] data = new byte[100 * 1024]; // 100KB
MEMORY_LEAK_CACHE.put(key, Arrays.asList(data));
```

### 缓慢累积
- 每条消息消耗约 100KB（可配置）
- 通过控制消息发送速度，实现缓慢增长
- 每处理10条消息打印一次内存统计
- 内存使用率超过80%时发出警告

### 监控机制
```java
// 实时内存统计
Runtime runtime = Runtime.getRuntime();
long usedMemory = totalMemory - freeMemory;
double usedPercentage = (double) usedMemory / maxMemory * 100;

// 定期打印日志
log.info("📊 内存统计 - 消息数: {}, 已用: {}, 使用率: {:.2f}%", 
         messageCount, formatMemory(usedMemory), usedPercentage);
```

---

## ⚠️ 安全提醒

**所有 OOM 测试功能仅用于教学和测试目的！**

### 禁止行为
❌ 在生产环境运行  
❌ 在未备份数据的环境运行  
❌ 在共享服务器上运行  
❌ 在没有监控的情况下运行  

### 推荐行为
✅ 在隔离的测试环境运行  
✅ 提前备份重要数据  
✅ 使用监控工具观察  
✅ 设置合理的堆内存限制  

---

## 🔧 故障排除

### 问题1: 脚本无法执行
```bash
# Linux/Mac: 添加执行权限
chmod +x start-oom-test.sh quick-oom-test.sh
```

### 问题2: 应用启动失败
```bash
# 检查端口占用
netstat -an | findstr 8080    # Windows
lsof -i :8080                 # Linux/Mac

# 检查 RocketMQ 是否启动
# 确保 NameServer 在 127.0.0.1:9876 运行
```

### 问题3: 内存不增长
```bash
# 1. 确认已启动内存泄漏模式
curl -X GET "http://localhost:8080/oom/stats"

# 2. 检查 leakEnabled 是否为 true
# 3. 如果为 false，调用启动接口
curl -X POST "http://localhost:8080/oom/start?sizeKB=100"
```

### 问题4: OOM 太快或太慢
```bash
# 太快：增加堆内存或减小对象大小
-Xmx512m -Xms256m
curl -X POST "http://localhost:8080/oom/start?sizeKB=50"

# 太慢：减小堆内存或增大对象大小
-Xmx128m -Xms64m
curl -X POST "http://localhost:8080/oom/start?sizeKB=500"
```

---

## 📞 技术支持

如有问题，请参考：
1. [OOM_TEST_GUIDE.md](OOM_TEST_GUIDE.md) - 完整指南
2. [OOM_QUICK_START.md](OOM_QUICK_START.md) - 快速入门
3. [README.md](README.md) - 项目总览

---

**文档版本**: v1.0  
**最后更新**: 2025-11-05  
**作者**: demo

