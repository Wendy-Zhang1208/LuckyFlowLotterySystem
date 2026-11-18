-- KEYS[1]：每日次数资源 key；KEYS[2]：全局 reservation key。
-- ARGV[1..5]：RESERVED、REJECTED、RELEASED、CANCELLED、CONFIRMED 完整协议值。
-- ARGV[6]：release 先于 reserve 到达时，CANCELLED 终止标记的 TTL（秒）。
local cancellationTtlSeconds = tonumber(ARGV[6])
if not cancellationTtlSeconds or cancellationTtlSeconds <= 0 then
    return -3
end

local marker = redis.call('GET', KEYS[2])

if not marker then
    -- 补偿可能先于迟到的 reserve；CANCELLED 墓碑保证后者不能再增加次数。
    redis.call('SET', KEYS[2], ARGV[4], 'EX', cancellationTtlSeconds)
    return 0
end

if marker == ARGV[2] or marker == ARGV[3] or marker == ARGV[4] or marker == ARGV[5] then
    return 0
end

if marker ~= ARGV[1] then
    return -2
end

local currentValue = redis.call('GET', KEYS[1])
if not currentValue then
    return -1
end

local current = tonumber(currentValue)
local ttlMillis = redis.call('PTTL', KEYS[1])
if not current or current <= 0 or ttlMillis <= 0 then
    return -3
end

redis.call('DECR', KEYS[1])
-- RELEASED 继续沿用每日计数器的日界 TTL，不把历史状态带到下一业务日。
redis.call('SET', KEYS[2], ARGV[3], 'PX', ttlMillis)

return 1
