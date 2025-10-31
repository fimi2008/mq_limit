--[[
令牌桶限流算法

参数说明：
KEYS[1] - 限流key
ARGV[1] - 当前时间戳（毫秒）
ARGV[2] - 桶容量（最大令牌数）
ARGV[3] - 令牌生成速率（个/秒）

返回值：
1 - 允许通过（成功获取令牌）
0 - 限流拦截（令牌不足）
--]]

local key = KEYS[1]
local now = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local rate = tonumber(ARGV[3])

local info = redis.call('hmget', key, 'tokens', 'timestamp')
local tokens = tonumber(info[1])
local timestamp = tonumber(info[2])

if tokens == nil then
    -- 首次请求，初始化令牌桶
    tokens = limit
    timestamp = now
else
    -- 计算新增的令牌数
    local deltaTime = math.max(0, now - timestamp)
    local newTokens = math.floor(deltaTime * rate / 1000)
    tokens = math.min(limit, tokens + newTokens)
    timestamp = now
end

if tokens >= 1 then
    -- 消耗一个令牌
    tokens = tokens - 1
    redis.call('hmset', key, 'tokens', tokens, 'timestamp', timestamp)
    redis.call('expire', key, 60)
    return 1
else
    -- 令牌不足
    return 0
end

