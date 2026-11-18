-- KEYS[1]：奖品库存资源 key；KEYS[2]：全局 reservation key。
-- ARGV[1]：RELEASED/REJECTED/CANCELLED 终态 marker 的 TTL（秒）。
-- ARGV[2..6]：RESERVED、REJECTED、RELEASED、CANCELLED、CONFIRMED 完整协议值。
local markerTtlSeconds = tonumber(ARGV[1])
if not markerTtlSeconds or markerTtlSeconds <= 0 then
    return -3
end

local marker = redis.call('GET', KEYS[2])

if not marker then
    -- 先到的补偿写 CANCELLED，防止迟到 reserve 随后错误扣减库存。
    redis.call('SET', KEYS[2], ARGV[5], 'EX', markerTtlSeconds)
    return 0
end

if marker == ARGV[3] or marker == ARGV[4] or marker == ARGV[5] or marker == ARGV[6] then
    return 0
end

if marker ~= ARGV[2] then
    return -2
end

local inventoryValue = redis.call('GET', KEYS[1])
if not inventoryValue then
    return -1
end
local inventory = tonumber(inventoryValue)
if not inventory or inventory < 0 then
    return -3
end

redis.call('INCR', KEYS[1])
redis.call('SET', KEYS[2], ARGV[4], 'EX', markerTtlSeconds)

return 1
