--[[
滑动窗口限流算法

参数说明：
KEYS[1] - 限流key
ARGV[1] - 当前时间戳（毫秒）
ARGV[2] - 窗口开始时间戳（毫秒）
ARGV[3] - 限流次数
ARGV[4] - 窗口大小（秒）

返回值：
1 - 允许通过
0 - 限流拦截
--]]

local key = KEYS[1]
local now = tonumber(ARGV[1])
local windowStart = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local windowSize = tonumber(ARGV[4])

-- 移除窗口外的数据
redis.call('zremrangebyscore', key, 0, windowStart)

-- 获取当前窗口内的请求数
local current = redis.call('zcard', key)

if current < limit then
    -- 添加当前请求
    redis.call('zadd', key, now, now)
    -- 设置过期时间
    redis.call('expire', key, windowSize)
    return 1
else
    return 0
end

