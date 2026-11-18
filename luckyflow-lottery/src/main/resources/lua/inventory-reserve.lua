-- KEYS[1]：奖品库存资源 key；KEYS[2]：全局 reservation key。
-- ARGV[1]：REJECTED 终态 marker 的 TTL（秒）。
-- ARGV[2..6]：当前资源绑定的 RESERVED、REJECTED、RELEASED、CANCELLED、CONFIRMED 完整协议值。
local markerTtlSeconds = tonumber(ARGV[1])
if not markerTtlSeconds or markerTtlSeconds <= 0 then
    return -3
end

local marker = redis.call('GET', KEYS[2])
if marker then
    if marker == ARGV[2] then
        -- 相同 reservation 重试不能再次扣减；库存 key 丢失时必须暴露异常而非伪造成功。
        local reservedInventoryValue = redis.call('GET', KEYS[1])
        if not reservedInventoryValue then
            return -1
        end
        local reservedInventory = tonumber(reservedInventoryValue)
        if not reservedInventory or reservedInventory < 0 then
            return -3
        end
        return 1
    end
    if marker == ARGV[3] or marker == ARGV[4] or marker == ARGV[5] or marker == ARGV[6] then
        return 0
    end
    return -2
end

local inventoryValue = redis.call('GET', KEYS[1])
if not inventoryValue then
    return -1
end

local inventory = tonumber(inventoryValue)
if not inventory then
    return -3
end

if inventory < 0 then
    return -3
end

if inventory == 0 then
    -- 无库存是明确业务结果，写 REJECTED 后同一请求重试仍返回拒绝。
    redis.call('SET', KEYS[2], ARGV[3], 'EX', markerTtlSeconds)
    return 0
end

redis.call('DECR', KEYS[1])
-- RESERVED 代表库存已经真实扣减；在数据库 intent 落终态前不得自动过期。
redis.call('SET', KEYS[2], ARGV[2])

return 1
