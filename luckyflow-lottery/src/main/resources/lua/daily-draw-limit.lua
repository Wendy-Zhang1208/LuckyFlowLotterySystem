-- KEYS[1]：每日次数资源 key；KEYS[2]：全局 reservation key。
-- ARGV[1]：每日上限；ARGV[2]：到下一业务日的 TTL（秒）。
-- ARGV[3..7]：当前资源绑定的 RESERVED、REJECTED、RELEASED、CANCELLED、CONFIRMED 完整协议值。
local marker = redis.call('GET', KEYS[2])

if marker then
    if marker == ARGV[3] then
        -- 相同 reservation 的网络重试不能再次增加次数；资源 key 丢失也不能伪装成成功。
        local reservedCountValue = redis.call('GET', KEYS[1])
        if not reservedCountValue then
            return -1
        end
        local reservedCount = tonumber(reservedCountValue)
        if not reservedCount or reservedCount <= 0 or redis.call('PTTL', KEYS[1]) <= 0 then
            return -3
        end
        return 1
    end
    if marker == ARGV[4] or marker == ARGV[5] or marker == ARGV[6] or marker == ARGV[7] then
        return 0
    end
    -- 同一全局 reservation key 已绑定其他资源或包含非法协议值。
    return -2
end

local limit = tonumber(ARGV[1])
local ttlSeconds = tonumber(ARGV[2])
if not limit or limit <= 0 or not ttlSeconds or ttlSeconds <= 0 then
    return -3
end

local currentValue = redis.call('GET', KEYS[1])
local current = 0
local resourceTtlMillis = ttlSeconds * 1000
if currentValue then
    current = tonumber(currentValue)
    if not current or current < 0 then
        return -3
    end
    resourceTtlMillis = redis.call('PTTL', KEYS[1])
    if resourceTtlMillis <= 0 then
        return -3
    end
end

if current >= limit then
    -- 明确拒绝也需要落 reservation 终态，防止同一请求重试时改变结果。
    redis.call('SET', KEYS[2], ARGV[4], 'PX', resourceTtlMillis)
    return 0
end

local newCount = redis.call('INCR', KEYS[1])
if not currentValue then
    redis.call('EXPIRE', KEYS[1], ttlSeconds)
end
-- marker 与每日计数资源使用同一个日界，跨日后两者一起自然失效。
redis.call('SET', KEYS[2], ARGV[3], 'PX', resourceTtlMillis)

return 1
