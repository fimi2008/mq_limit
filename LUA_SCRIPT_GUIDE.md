# Lua 脚本使用指南

## 概述

本项目将 Redis Lua 脚本从 Java 代码中分离到 `resources/lua/` 目录，提高代码可维护性和可读性。

## 为什么使用 Lua 脚本？

### 1. 保证原子性

Redis Lua 脚本在执行时是原子性的，不会被其他命令打断：

```
❌ 不使用 Lua 脚本：
步骤1: ZREMRANGEBYSCORE key 0 windowStart  ← 可能被其他命令打断
步骤2: ZCARD key
步骤3: ZADD key score member

✅ 使用 Lua 脚本：
整个脚本作为一个原子操作执行，中间不会被打断
```

### 2. 减少网络往返

```
❌ 多次命令调用：
Java → Redis: ZREMRANGEBYSCORE (RTT 1)
Redis → Java: OK
Java → Redis: ZCARD (RTT 2)
Redis → Java: 3
Java → Redis: ZADD (RTT 3)
Redis → Java: OK

✅ Lua 脚本：
Java → Redis: EVAL script (RTT 1)
Redis → Java: result
```

### 3. 提高性能

单次网络请求 vs 多次网络请求：
- **不使用 Lua**：3次网络往返，约 3-6ms（本地）
- **使用 Lua**：1次网络往返，约 1-2ms（本地）

## 项目中的 Lua 脚本

### 1. 滑动窗口限流脚本

**文件位置**：`src/main/resources/lua/sliding_window_rate_limit.lua`

**功能**：实现精确的滑动窗口限流算法

**参数说明**：
```lua
KEYS[1] - 限流key（例如：rate_limit:sliding:third_party_api）
ARGV[1] - 当前时间戳（毫秒）
ARGV[2] - 窗口开始时间戳（毫秒）
ARGV[3] - 限流次数（例如：5）
ARGV[4] - 窗口大小（秒，例如：1）
```

**返回值**：
- `1` - 允许通过
- `0` - 限流拦截

**算法流程**：
```
1. 移除窗口外的过期数据
   ZREMRANGEBYSCORE key 0 windowStart
   
2. 统计窗口内的请求数
   current = ZCARD key
   
3. 判断是否超限
   if current < limit then
       添加当前请求
       ZADD key now now
       return 1
   else
       return 0
   end
```

**使用示例**：
```java
@Resource(name = "slidingWindowScript")
private RedisScript<Long> slidingWindowScript;

public boolean slidingWindowRateLimit(String key, int limit, int windowSize) {
    long now = Instant.now().toEpochMilli();
    long windowStart = now - windowSize * 1000L;
    String redisKey = "rate_limit:sliding:" + key;
    
    Long result = stringRedisTemplate.execute(
        slidingWindowScript,
        Collections.singletonList(redisKey),
        String.valueOf(now),
        String.valueOf(windowStart),
        String.valueOf(limit),
        String.valueOf(windowSize)
    );
    
    return result != null && result == 1;
}
```

### 2. 令牌桶限流脚本

**文件位置**：`src/main/resources/lua/token_bucket_rate_limit.lua`

**功能**：实现令牌桶限流算法，支持突发流量

**参数说明**：
```lua
KEYS[1] - 限流key
ARGV[1] - 当前时间戳（毫秒）
ARGV[2] - 桶容量（最大令牌数）
ARGV[3] - 令牌生成速率（个/秒）
```

**返回值**：
- `1` - 成功获取令牌
- `0` - 令牌不足

**算法流程**：
```
1. 获取当前令牌数和上次更新时间
   info = HMGET key 'tokens' 'timestamp'
   
2. 计算新增令牌数
   deltaTime = now - timestamp
   newTokens = floor(deltaTime * rate / 1000)
   tokens = min(limit, tokens + newTokens)
   
3. 尝试消耗一个令牌
   if tokens >= 1 then
       tokens = tokens - 1
       HMSET key 'tokens' tokens 'timestamp' now
       return 1
   else
       return 0
   end
```

**使用示例**：
```java
@Resource(name = "tokenBucketScript")
private RedisScript<Long> tokenBucketScript;

public boolean tokenBucketRateLimit(String key, int capacity, double rate) {
    long now = Instant.now().toEpochMilli();
    String redisKey = "rate_limit:token:" + key;
    
    Long result = stringRedisTemplate.execute(
        tokenBucketScript,
        Collections.singletonList(redisKey),
        String.valueOf(now),
        String.valueOf(capacity),
        String.valueOf(rate)
    );
    
    return result != null && result == 1;
}
```

## 脚本配置类

**文件位置**：`src/main/java/com/example/mqlimitdemo/config/RedisLuaScriptConfig.java`

```java
@Configuration
public class RedisLuaScriptConfig {

    /**
     * 滑动窗口限流脚本
     */
    @Bean(name = "slidingWindowScript")
    public DefaultRedisScript<Long> slidingWindowScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/sliding_window_rate_limit.lua")));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 令牌桶限流脚本
     */
    @Bean(name = "tokenBucketScript")
    public DefaultRedisScript<Long> tokenBucketScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/token_bucket_rate_limit.lua")));
        script.setResultType(Long.class);
        return script;
    }
}
```

## 优化前后对比

### 优化前（脚本在 Java 代码中）

```java
public boolean slidingWindowRateLimit(String key, int limit, int windowSize) {
    // 硬编码在Java代码中，约70行字符串拼接
    String luaScript = 
        "local key = KEYS[1]\n" +
        "local now = tonumber(ARGV[1])\n" +
        "local windowStart = tonumber(ARGV[2])\n" +
        // ... 更多拼接 ...
        "end";
    
    RedisScript<Long> script = RedisScript.of(luaScript, Long.class);
    // ...
}
```

**缺点**：
- ❌ 代码冗长，可读性差
- ❌ 难以维护和调试
- ❌ 字符串拼接容易出错
- ❌ 没有语法高亮
- ❌ 无法单独测试脚本

### 优化后（脚本在 resources 目录）

**Lua 脚本文件**：`resources/lua/sliding_window_rate_limit.lua`
```lua
-- 完整的注释和语法高亮
local key = KEYS[1]
local now = tonumber(ARGV[1])
-- ...
```

**Java 代码**：
```java
@Resource(name = "slidingWindowScript")
private RedisScript<Long> slidingWindowScript;

public boolean slidingWindowRateLimit(String key, int limit, int windowSize) {
    // 简洁清晰
    Long result = stringRedisTemplate.execute(
        slidingWindowScript,
        Collections.singletonList(redisKey),
        now, windowStart, limit, windowSize
    );
    return result != null && result == 1;
}
```

**优点**：
- ✅ 代码简洁，可读性强
- ✅ Lua 脚本易于维护
- ✅ 支持语法高亮
- ✅ 可以单独测试脚本
- ✅ 版本控制友好

## 如何添加新的 Lua 脚本

### 步骤 1：创建 Lua 脚本文件

在 `src/main/resources/lua/` 目录下创建新脚本：

```lua
-- my_custom_rate_limit.lua

--[[
自定义限流算法说明
--]]

local key = KEYS[1]
local arg1 = ARGV[1]

-- 实现你的逻辑
local result = redis.call('GET', key)

return result
```

### 步骤 2：配置 Bean

在 `RedisLuaScriptConfig.java` 中添加：

```java
@Bean(name = "myCustomScript")
public DefaultRedisScript<Long> myCustomScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("lua/my_custom_rate_limit.lua")));
    script.setResultType(Long.class);
    log.info("加载 Lua 脚本：自定义限流");
    return script;
}
```

### 步骤 3：使用脚本

```java
@Component
public class MyRateLimiter {
    
    @Resource(name = "myCustomScript")
    private RedisScript<Long> myCustomScript;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    public boolean rateLimit(String key, String arg) {
        Long result = stringRedisTemplate.execute(
            myCustomScript,
            Collections.singletonList(key),
            arg
        );
        return result != null && result == 1;
    }
}
```

## 脚本调试

### 1. 在 Redis CLI 中测试

```bash
# 连接 Redis
redis-cli

# 加载脚本
SET test_key test_value

# 执行脚本（需要将脚本内容作为字符串）
EVAL "local key = KEYS[1]; return redis.call('GET', key)" 1 test_key
```

### 2. 使用 Redis Desktop Manager

推荐工具：
- RedisInsight（官方工具）
- Another Redis Desktop Manager
- Medis（Mac）

可以直接在工具中执行和调试 Lua 脚本。

### 3. 单元测试

```java
@SpringBootTest
public class LuaScriptTest {
    
    @Resource(name = "slidingWindowScript")
    private RedisScript<Long> script;
    
    @Resource
    private StringRedisTemplate redis;
    
    @Test
    public void testSlidingWindow() {
        String key = "test:key";
        long now = System.currentTimeMillis();
        long windowStart = now - 1000;
        
        Long result = redis.execute(
            script,
            Collections.singletonList(key),
            String.valueOf(now),
            String.valueOf(windowStart),
            "5",
            "1"
        );
        
        assertEquals(1L, result);
    }
}
```

## 性能监控

### 查看脚本执行统计

```bash
# 查看慢日志
redis-cli SLOWLOG GET 10

# 查看脚本缓存
redis-cli SCRIPT EXISTS sha1_value

# 清空脚本缓存
redis-cli SCRIPT FLUSH
```

### 性能优化建议

1. **脚本缓存**：Spring 会自动缓存脚本的 SHA1 值
2. **避免大量数据**：Lua 脚本中不要处理大量数据
3. **使用局部变量**：Lua 中尽量使用 `local` 关键字
4. **避免长时间操作**：脚本执行时会阻塞 Redis

## 常见问题

### Q1: 脚本文件找不到

**错误**：`ScriptException: ERR Error compiling script`

**解决**：
1. 检查文件路径：`resources/lua/xxx.lua`
2. 确保文件在 classpath 中
3. Maven 构建时包含 lua 文件

```xml
<build>
    <resources>
        <resource>
            <directory>src/main/resources</directory>
            <includes>
                <include>**/*.lua</include>
            </includes>
        </resource>
    </resources>
</build>
```

### Q2: 脚本修改后不生效

**原因**：Spring 缓存了脚本的 SHA1 值

**解决**：
1. 重启应用
2. 或清空 Redis 脚本缓存：`SCRIPT FLUSH`

### Q3: 脚本执行报错

**排查步骤**：
1. 在 Redis CLI 中单独测试脚本
2. 检查参数个数和类型
3. 查看 Redis 日志
4. 使用 `redis.log()` 输出调试信息

## 最佳实践

### 1. 脚本命名规范

```
功能_算法_操作.lua
例如：
- sliding_window_rate_limit.lua
- token_bucket_rate_limit.lua
- fixed_window_rate_limit.lua
```

### 2. 添加详细注释

```lua
--[[
脚本名称：滑动窗口限流
功能描述：使用 Redis Sorted Set 实现精确的滑动窗口限流
作者：demo
创建时间：2024-01-01
最后修改：2024-01-10

参数说明：
KEYS[1] - 限流key
ARGV[1] - 当前时间戳
...

返回值：
1 - 成功
0 - 失败
--]]
```

### 3. 错误处理

```lua
local key = KEYS[1]

if not key then
    return redis.error_reply("key is required")
end

local result = redis.call('GET', key)
if not result then
    return 0
end

return 1
```

### 4. 版本管理

```lua
-- Version: 1.0.0
-- Last Updated: 2024-01-01
-- Changes: Initial version
```

## 相关资源

- [Redis Lua 脚本官方文档](https://redis.io/docs/manual/programmability/eval-intro/)
- [Lua 5.1 参考手册](https://www.lua.org/manual/5.1/)
- [Redis 命令参考](https://redis.io/commands/)

## 总结

将 Lua 脚本分离到 resources 目录的优势：

| 方面 | 优化前 | 优化后 |
|------|--------|--------|
| 可读性 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 可维护性 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 调试难度 | 困难 | 简单 |
| 代码行数 | 多 | 少 |
| 语法高亮 | 无 | 有 |
| 版本控制 | 差 | 好 |

**推荐做法**：
✅ 所有 Lua 脚本都放在 `resources/lua/` 目录
✅ 通过配置类加载脚本
✅ 添加详细注释和文档
✅ 进行充分的单元测试

